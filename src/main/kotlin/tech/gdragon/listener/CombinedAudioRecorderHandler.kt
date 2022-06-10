package tech.gdragon.listener

import com.squareup.tape.QueueFile
import de.sciss.jump3r.lowlevel.LameEncoder
import io.azam.ulidj.ULID
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import mu.KotlinLogging
import mu.withLoggingContext
import net.dv8tion.jda.api.audio.AudioReceiveHandler
import net.dv8tion.jda.api.audio.CombinedAudio
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.VoiceChannel
import org.apache.commons.io.FileUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import tech.gdragon.BotUtils
import tech.gdragon.data.Datastore
import tech.gdragon.db.dao.Channel
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.dao.Recording
import tech.gdragon.db.dtf
import tech.gdragon.db.now
import tech.gdragon.db.nowUTC
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class RecordingDisposable {
  private var compositeDisposable: CompositeDisposable? = null

  fun add(disposable: Disposable) {
    if (compositeDisposable == null) {
      compositeDisposable = CompositeDisposable()
    }
    compositeDisposable?.add(disposable)
  }

  /**
   * Call dispose on all Disposable's via CompositeDisposable and remove reference to compositeDisposable
   */
  fun dispose() {
    compositeDisposable?.dispose()
    reset()
  }

  /**
   * Remove reference to compositeDisposable
   */
  fun reset() {
    compositeDisposable = null
  }
}

data class RecordingQueue(val fileBuffer: File) : QueueFile(fileBuffer)

class CombinedAudioRecorderHandler(
  var volume: Double,
  val voiceChannel: VoiceChannel,
  val defaultChannel: TextChannel
) : AudioReceiveHandler, KoinComponent {
  companion object {
    private const val AFK_MINUTES = 2
    private const val AFK_LIMIT = (AFK_MINUTES * 60 * 1000) / 20            // 2 minutes in ms over 20ms increments
    private const val MAX_RECORDING_MB = 110L
    private const val MIN_RECORDING_SIZE = 5L * 1024 * 1024                 // 5MB
    private const val MAX_RECORDING_SIZE = MAX_RECORDING_MB * 1024 * 1024   // 110MB
    private const val DISCORD_MAX_RECORDING_SIZE = 8L * 1024 * 1024         // 8MB
    private const val BUFFER_TIMEOUT = 200L                                 // 200 milliseconds
    private const val BUFFER_MAX_COUNT = 8
    private const val BITRATE = 128                                         // 128 kbps
    private const val BYTES_PER_SECOND = 16_000L                            // 128 kbps == 16000 bytes per second
  }

  private val logger = KotlinLogging.logger { }
  private val datastore: Datastore by inject()
  private val dataDirectory: String = getKoin().getProperty("BOT_DATA_DIR", "./")
  private val fileFormat: String = getKoin().getProperty("BOT_FILE_FORMAT", "mp3").lowercase()
  private val standalone = getKoin().getProperty<String>("BOT_STANDALONE").toBoolean()
  private val vbr = getKoin().getProperty<String>("BOT_MP3_VBR").toBoolean()
  private val span: Span = getKoin().get<Tracer>()
    .spanBuilder("Create Recording")
    .startSpan()

  // State-licious
  private var subject: PublishSubject<CombinedAudio>? = null
  private var single: Single<RecordingQueue?>? = null
  private val compositeDisposable = RecordingDisposable()
  private var ulid: String? = null
  private var recordingRecord: Recording? = null

  private var canReceive = true
  private var afkCounter = 0

  private var filename: String? = null

  private var recordingSize: Long = 0
  private var limitWarning: Boolean = false

  private var silencedUsers: MutableSet<Long> = mutableSetOf()

  private var scope: Scope? = null

  val session: String
    get() = ulid ?: ""

  init {
    scope = span.makeCurrent()
    single = createRecording()
  }

  /**
   * Checks if everyone in voice chat is afk. Super malformed function as it has
   * side effects and triggers messages outside of the scope
   */
  private fun isAfk(userCount: Int): Boolean {
    if (standalone) {
      return false
    } else {
      if (userCount == 0) afkCounter++ else afkCounter = 0

      val isAfk = afkCounter >= AFK_LIMIT

      if (isAfk) {
        withLoggingContext("guild" to voiceChannel.guild.name, "voice-channel" to voiceChannel.name) {
          logger.debug { "AFK detected." }
        }

        BotUtils.sendMessage(
          defaultChannel,
          ":sleeping: _No audio detected in the last **$AFK_MINUTES** minutes, leaving **<#${voiceChannel.id}>**._"
        )
        thread {
          val save = BotUtils.autoSave(voiceChannel.guild)
          BotUtils.leaveVoiceChannel(voiceChannel, defaultChannel, save)
        }
      }

      return isAfk
    }
  }

  private fun createRecording(): Single<RecordingQueue?>? {
    ulid = ULID.random()
    recordingRecord = transaction {
      Guild.findById(voiceChannel.guild.idLong)?.let {
        Recording.new(ulid) {
          channel = Channel.findOrCreate(voiceChannel.idLong, voiceChannel.name, it)
          guild = it
        }
      }
    }

    subject = PublishSubject.create()
    filename = "$dataDirectory/recordings/$ulid.$fileFormat"
    val queueFilename = "$dataDirectory/recordings/$ulid.queue"
    val queueFile = RecordingQueue(File(queueFilename))
    canReceive = true

    span.run {
      setAttribute("session-id", ulid)
    }

    val encoder = LameEncoder(
      AudioReceiveHandler.OUTPUT_FORMAT,
      BITRATE,
      LameEncoder.CHANNEL_MODE_AUTO,
      LameEncoder.QUALITY_HIGHEST,
      vbr
    )

    BotUtils.sendMessage(
      defaultChannel, """:red_circle: **Recording audio on <#${voiceChannel.id}>**
      |_Session ID: `${session}`_
      |.
      |.
      |.
      |:warning: _`save` before stopping recording, otherwise recording will be deleted FOREVER!_
      """.trimMargin()
    )
    logger.info { "Creating recording session - $queueFilename" }

    val singleObservable = subject
      ?.doOnNext { isAfk(it.users.size) }
      ?.map { it.getAudioData(volume) }
      ?.buffer(BUFFER_TIMEOUT, TimeUnit.MILLISECONDS, BUFFER_MAX_COUNT)
      ?.flatMap { byteArrays ->
        val baos = ByteArrayOutputStream()

        byteArrays.forEach {
          if (fileFormat == "pcm") {
            baos.writeBytes(it)
          } else {
            val buffer = ByteArray(it.size)
            val bytesEncoded = encoder.encodeBuffer(it, 0, it.size, buffer)
            baos.write(buffer, 0, bytesEncoded)
          }
        }

        Observable.fromArray(baos.toByteArray())
      }
      ?.doOnNext {
        val percentage = recordingSize * 100 / MAX_RECORDING_SIZE
        if (!standalone && (percentage >= 90 && !limitWarning)) {
          BotUtils.sendMessage(
            defaultChannel,
            ":warning:_You've reached $percentage% of your recording limit, please save to start a new session._"
          )
          limitWarning = true
        }
      }
      ?.observeOn(Schedulers.io())
      ?.collectInto(queueFile) { queue, bytes ->

        while (!standalone && recordingSize + bytes.size > MAX_RECORDING_SIZE) {
          recordingSize -= queue?.peek()?.size ?: 0
          queue?.remove()
        }

        try {
          queue?.add(bytes)
          recordingSize += bytes.size
        } catch (e: IOException) {
          logger.warn {
            "${e.message} - Queue file has been closed: $ulid"
          }
        }
      }

    val disposable = singleObservable?.subscribe { _, e ->
      e?.let { ex ->
        logger.error(ex) { "Error on subscription on createRecording: $ulid" }
      }
    }


    disposable?.let(compositeDisposable::add)

    return singleObservable
  }

  fun saveRecording(
    voiceChannel: VoiceChannel?,
    textChannel: TextChannel,
    resumeRecording: Boolean = true
  ): Pair<Recording?, Semaphore> {
    canReceive = false
    val recordingLock = Semaphore(1, false)
    val recordingId = ulid

    logger.debug { "Creating subscription for recording: $recordingId" }
    val disposable = single?.subscribe { queueFile, e ->
      e?.let { ex ->
        logger.error(ex) { "Error on subscription on saveRecording: $recordingId" }
      }

      val recordingFile = queueFile?.let {
        logger.info { "Completed recording: $recordingId, queue file size: ${it.size()}" }
        File(it.fileBuffer.canonicalPath.replace("queue", "mp3"))
      }

      FileOutputStream(recordingFile!!).use {
        withLoggingContext("session-id" to session) {
          queueFile.apply {
            try {
              forEach { stream, _ ->
                stream.transferTo(it)
              }

              logger.info {
                "Saving audio file ${recordingFile.name} - ${FileUtils.byteCountToDisplaySize(recordingFile.length())}."
              }

              logger.debug {
                "Recording size in bytes: $recordingSize"
              }

              uploadRecording(recordingFile, voiceChannel, textChannel)
            } catch (e: IOException) {
              logger.warn(e) {
                "Could not generate MP3 file from Queue: ${recordingFile.absolutePath}: ${queueFile.fileBuffer.canonicalPath}"
              }

              val errorMessage =
                """|:no_entry_sign: _Error creating recording, please visit support server and provide Session ID._
                   |_Session ID: `$session`_
                   |""".trimMargin()

              BotUtils.sendMessage(textChannel, errorMessage)
            } finally {
              close()

              logger.debug {
                "Releasing lock in saveRecording subscription on id: $recordingId"
              }
              recordingLock.release(1)
            }
          }
        }
      }
    }

    // Add subscriber to composite disposable
    disposable?.let(compositeDisposable::add)

    logger.debug {
      "Acquiring lock in saveRecording on recording: $recordingId"
    }
    recordingLock.acquire(1) // what could go wrong?
    logger.debug { "Marking observable as completed for recording: $recordingId" }
    subject?.onComplete()

    val recording = recordingRecord

    // Resume recording
    if (resumeRecording) {
      compositeDisposable.reset()
      createRecording()
      logger.info { "Cannot wait! Creating a new recording: $recordingId" }
    }

    return Pair(recording, recordingLock)
  }

  fun saveClip(seconds: Long, voiceChannel: VoiceChannel?, channel: TextChannel) {
/*    // Stop recording so that we can copy Queue File
    canReceive = false

    val path = Paths.get(queueFilename)
    val clipPath = Paths.get("$dataDirectory/recordings/clip-${UUID.randomUUID()}.queue")

    // Copy the original Queue File so that we can resume receiving audio
    Files.copy(path, clipPath, StandardCopyOption.REPLACE_EXISTING)
    canReceive = true

    val queueFile = QueueFile(clipPath.toFile())
    val filenameExtension = if (pcmMode) "pcm" else "mp3"
    val recording = File(clipPath.toString().replace("queue", filenameExtension))
    var clipRecordingSize = recordingSize.toLong()

    // Reduce the queue size until it's just over the expected clip size
    while (clipRecordingSize - queueFile.peek().size > BYTES_PER_SECOND * seconds) {
      queueFile.remove()
      clipRecordingSize -= queueFile.peek().size
    }

    FileOutputStream(recording).use {
      queueFile.apply {
        forEach { stream, _ ->
          stream.transferTo(it)
        }

        close()
        Files.delete(clipPath)
      }
    }

    val recordingSizeInMB = FileUtils.byteCountToDisplaySize(recording.length())
    logger.info {
      "Saving audio clip ${recording.name} - $recordingSizeInMB."
    }

    withLoggingContext("session-id" to session) {
      uploadRecording(recording, voiceChannel, channel)
    }*/
  }

  private fun uploadRecording(recording: File, voiceChannel: VoiceChannel?, channel: TextChannel) {
    if (recording.length() <= 0) {
      val message = ":no_entry_sign: _Recording is empty, not uploading._"
      BotUtils.sendMessage(channel, message)

      transaction {
        recordingRecord?.delete()
      }
    } else {
      val filename = if (standalone) "${voiceChannel?.name}-${dtf.print(nowUTC())}.$fileFormat" else recording.name
      // Upload to Discord
      val attachment =
        if (recording.length() < DISCORD_MAX_RECORDING_SIZE) {
          BotUtils.uploadFile(channel, recording, filename)
            ?.attachments
            ?.first()
        } else null

      // Upload to Minio
      if (recording.length() in MIN_RECORDING_SIZE until MAX_RECORDING_SIZE || standalone) {
        val recordingKey = if (standalone) {
          "${channel.guild.id}/$filename"
        } else {
          "${channel.guild.id}/${recording.name}"
        }
        try {
          val result = datastore.upload(recordingKey, recording)

          val message = """|:microphone2: **Recording for <#${voiceChannel?.id}> has been uploaded!**
                           |${if (!standalone) result.url else "`" + result.url + "`"}
                           |${if (!standalone) "\n\n_Recording will only be available for 24hrs_" else ""}
                           |""".trimMargin()

          BotUtils.sendMessage(channel, message)

          transaction {
            recordingRecord?.apply {
              size = result.size
              modifiedOn = result.timestamp
              url = result.url
            }
          }

          cleanup(recording)
        } catch (e: Exception) {
          logger.error(e) {
            "Error uploading recording."
          }

          val errorMessage =
            """|:no_entry_sign: _Error uploading recording, please visit support server and provide Session ID._
                                |_Session ID: `$session`_
                                |""".trimMargin()

          BotUtils.sendMessage(channel, errorMessage)
        }
      } else {
        transaction {
          recordingRecord?.apply {
            size = recording.length()
            modifiedOn = now()
            url = attachment?.proxyUrl ?: "Discord Only"
          }
        }
        cleanup(recording)
      }
    }
  }

  fun disconnect(dispose: Boolean = true, recording: Recording? = null, recordingLock: Semaphore? = null) {
    // Stop accepting audio from Discord
    canReceive = false

    if (dispose) {
      compositeDisposable.dispose()
    }

    // Clean up queue file
    single
      ?.doOnError { error ->
        logger.warn(error) {
          "Couldn't clean up properly due to Exception."
        }
      }
      ?.subscribe { queueFile, _ ->

        queueFile?.let {
          recordingLock?.let { lock ->
            lock.acquire(1)
            logger.warn {
              "Acquiring lock in disconnect subscription: ${it.fileBuffer.canonicalPath}"
            }
          }
          try {
            it.close()
            val mp3File = File(it.fileBuffer.canonicalPath.replace("queue", "mp3"))
            if (!standalone && (mp3File.exists() && 0 < mp3File.length())) {
              logger.info {
                "Delete queue file: ${it.fileBuffer.name}"
              }
              Files.deleteIfExists(Paths.get(it.fileBuffer.toURI()))
            } else {
              logger.info {
                "Skip deleting queue file: ${it.fileBuffer.name}, mp3 file size: ${mp3File.length()}"
              }
            }
          } catch (e: FileSystemException) {
            logger.warn(e) {
              "Couldn't delete ${it.fileBuffer.canonicalPath}"
            }
          }

          // Delete database entry if no URL
          recording?.apply {
            transaction {
              if (url.isNullOrEmpty())
                delete()
            }
          }
          recordingLock?.release(1)
        }
      }

    // Shut off the Observable
    subject?.onComplete()

    // Close Spans
    span.end()
    scope?.close()
  }

  private fun cleanup(recording: File) {
    val isDeleteSuccess = recording.delete()
    logger.info { "Deleting file ${recording.name}..." }

    if (isDeleteSuccess)
      logger.info { "Successfully deleted file ${recording.name}." }
    else
      logger.warn { "Could not delete file ${recording.name}." }
  }

  fun silenceUser(user: User) = silencedUsers.add(user.idLong)

  override fun canReceiveUser(): Boolean = false

  override fun canReceiveCombined(): Boolean = canReceive

  override fun handleCombinedAudio(combinedAudio: CombinedAudio) {
    subject?.onNext(combinedAudio)
  }

  override fun includeUserInCombinedAudio(user: User): Boolean {
    return !silencedUsers.contains(user.idLong)
  }
}
