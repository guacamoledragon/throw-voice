package tech.gdragon.listener

import com.squareup.tape.QueueFile
import de.sciss.jump3r.lowlevel.LameEncoder
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import mu.KotlinLogging
import net.dv8tion.jda.core.audio.AudioReceiveHandler
import net.dv8tion.jda.core.audio.CombinedAudio
import net.dv8tion.jda.core.audio.UserAudio
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.VoiceChannel
import org.apache.commons.io.FileUtils
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.data.DataStore
import tech.gdragon.db.dao.Channel
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.dao.Recording
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class CombinedAudioRecorderHandler(val volume: Double, val voiceChannel: VoiceChannel, val defaultChannel: TextChannel?) : AudioReceiveHandler {
  companion object {
    private const val AFK_LIMIT = (2 * 60 * 1000) / 20                      // 2 minutes in ms over 20ms increments
    private const val MAX_RECORDING_MB = 110
    private const val MAX_RECORDING_SIZE = MAX_RECORDING_MB * 1024 * 1024   // 8MB
    private const val BUFFER_TIMEOUT = 200L                                 // 200 milliseconds
    private const val BUFFER_MAX_COUNT = 8
    private const val BITRATE = 128                                         // 128 kbps
    private const val BYTES_PER_SECOND = 16_000L                            // 128 kbps == 16000 bytes per second
  }

  private val logger = KotlinLogging.logger { }
  private val datastore = DataStore.createDataStore(System.getenv("DS_BUCKET"))
  private val dataDirectory: String = System.getenv("DATA_DIR") ?: ""
  private val pcmMode: Boolean = System.getenv("PCM_MODE").isNullOrEmpty().not()

  // State-licious
  private var subject: Subject<CombinedAudio>? = null
  private var subscription: Disposable? = null
  private var uuid: UUID? = null
  private var queueFile: QueueFile? = null
  private var recordingRecord: Recording? = null

  private var canReceive = true
  private var afkCounter = 0

  private var filename: String? = null
  private var queueFilename: String? = null
  private var recordingSize: Int = 0

  init {
    subscription = createRecording()
  }

  /**
   * Checks if everyone in voice chat is afk. Super malformed function as it has
   * side effects and triggers messages outside of the scope
   */
  private fun isAfk(userCount: Int): Boolean {
    if (userCount == 0) afkCounter++ else afkCounter = 0

    val isAfk = afkCounter >= AFK_LIMIT

    if (isAfk) {
      logger.info("{}#{}: AFK detected.", voiceChannel.guild.name, voiceChannel.name)

      BotUtils.sendMessage(defaultChannel, "_:sleeping: No audio detected in the last 2 minutes, leaving <#${voiceChannel.id}>._")

      thread(start = true) {
        if (BotUtils.autoSave(voiceChannel.guild))
          saveRecording(voiceChannel, defaultChannel)
        BotUtils.leaveVoiceChannel(voiceChannel)
      }
    }

    return isAfk
  }

  private fun createRecording(): Disposable? {
    recordingRecord = transaction {
      Guild.findById(voiceChannel.guild.idLong)?.let {
        Recording.new {
          channel = Channel.findOrCreate(voiceChannel.idLong, voiceChannel.name, it)
          guild = it
        }
      }
    }

    subject = PublishSubject.create()
    uuid = UUID.randomUUID()
    val filenameExtension = if(pcmMode) "pcm" else "mp3"
    filename = "$dataDirectory/recordings/$uuid.$filenameExtension"
    queueFilename = "$dataDirectory/recordings/$uuid.queue"
    queueFile = QueueFile(File(queueFilename))
    canReceive = true

    val encoder = LameEncoder(AudioReceiveHandler.OUTPUT_FORMAT, BITRATE, LameEncoder.CHANNEL_MODE_AUTO, LameEncoder.QUALITY_HIGHEST, true)

    logger.info("{}#{}: Creating recording file - {}", voiceChannel.guild.name, voiceChannel.name, queueFilename)
    return subject
      ?.map { it.getAudioData(volume) }
      ?.buffer(BUFFER_TIMEOUT, TimeUnit.MILLISECONDS, BUFFER_MAX_COUNT)
      ?.flatMap { byteArrays ->
        val baos = ByteArrayOutputStream()

        byteArrays.forEach {
          if (pcmMode) {
            baos.writeBytes(it)
          } else {
            val buffer = ByteArray(it.size)
            val bytesEncoded = encoder.encodeBuffer(it, 0, it.size, buffer)
            baos.write(buffer, 0, bytesEncoded)
          }
        }

        Observable.fromArray(baos.toByteArray())
      }
      ?.collectInto(queueFile!!) { queue, bytes ->

        while (recordingSize + bytes.size > MAX_RECORDING_SIZE) {
          recordingSize -= queue.peek()?.size ?: 0
          queue.remove()
        }
        queue.add(bytes)
        recordingSize += bytes.size
      }
      ?.subscribe { _, e ->
        e?.let {
          logger.error("An error occurred in the recording pipeline: ${it.message}", it)
        }
      }
  }

  fun saveRecording(voiceChannel: VoiceChannel?, textChannel: TextChannel?) {
    canReceive = false
    subscription?.dispose()

    val recording = File(filename)

    FileOutputStream(recording).use {
      queueFile?.apply {
        forEach { stream, _ ->
          stream.transferTo(it)
        }

        clear()
        close()
        File(queueFilename).delete()
      }
    }

    logger.info {
      "Saving audio file ${recording.name} - ${FileUtils.byteCountToDisplaySize(recording.length())}."
    }

    logger.debug {
      "Recording size in bytes: $recordingSize"
    }

    uploadRecording(recording, voiceChannel, textChannel)

    // Resume recording
    subscription = createRecording()
  }

  fun saveClip(seconds: Long, voiceChannel: VoiceChannel?, channel: TextChannel?) {
    // Stop recording so that we can copy Queue File
    canReceive = false

    val path = Paths.get(queueFilename)
    val clipPath = Paths.get("$dataDirectory/recordings/clip-${UUID.randomUUID()}.queue")

    // Copy the original Queue File so that we can resume receiving audio
    Files.copy(path, clipPath, StandardCopyOption.REPLACE_EXISTING)
    canReceive = true

    val queueFile = QueueFile(clipPath.toFile())
    val recording = File(clipPath.toString().replace("queue", "mp3"))
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
    logger.info("{}#{}: Saving audio clip {} - {}.", voiceChannel?.guild?.name, voiceChannel?.name, recording.name, recordingSizeInMB)

    uploadRecording(recording, voiceChannel, channel)
  }

  private fun uploadRecording(recording: File, voiceChannel: VoiceChannel?, channel: TextChannel?) {
    if (recording.length() < MAX_RECORDING_SIZE) {
      val recordingKey = "/${channel?.guild?.id}/${recording.name}"
      val result = datastore.upload(recordingKey, recording)

      val message = """|:microphone2: **Recording for <#${voiceChannel?.id}> has been uploaded!**
                       |${result.url}
                       |
                       |_Recording will only be available for 24hrs_
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

    } else {
      val recordingSize = FileUtils.byteCountToDisplaySize(recording.length())
      BotUtils.sendMessage(channel, "Could not upload, file too large: $recordingSize.")
    }
  }

  fun disconnect() {
    canReceive = false

    try {
      subject?.onComplete()
    } catch (e: Exception) {
      logger.warn(e) {
        "Issue calling `onComplete` on CombinedAudioRecorderHandler: ${e.message}"
      }
    }

    subscription?.dispose()
    queueFile?.apply {
      clear()
      close()
      File(queueFilename).delete()
    }

    transaction {
      recordingRecord?.apply {
        if (url.isNullOrEmpty())
          delete()
      }
    }
  }

  private fun cleanup(recording: File) {
    val isDeleteSuccess = recording.delete()
    logger.info("Deleting file {}...", recording.name)

    if (isDeleteSuccess)
      logger.info("Successfully deleted file {}.", recording.name)
    else
      logger.error("Could not delete file {}.", recording.name)
  }

  override fun canReceiveUser(): Boolean = false

  override fun canReceiveCombined(): Boolean = canReceive

  override fun handleCombinedAudio(combinedAudio: CombinedAudio) {
    if (!isAfk(combinedAudio.users.size)) {
      subject?.onNext(combinedAudio)
    }
  }

  override fun handleUserAudio(userAudio: UserAudio?) = TODO("Not implemented.")
}
