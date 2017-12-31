package tech.gdragon.listener

import com.squareup.tape.QueueFile
import de.sciss.jump3r.lowlevel.LameEncoder
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.audio.AudioReceiveHandler
import net.dv8tion.jda.core.audio.CombinedAudio
import net.dv8tion.jda.core.audio.UserAudio
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.VoiceChannel
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import tech.gdragon.BotUtils
import tech.gdragon.db.dao.Guild
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class CombinedAudioRecorderHandler(val volume: Double, val voiceChannel: VoiceChannel) : AudioReceiveHandler {
  companion object {
    private const val AFK_LIMIT = (2 * 60 * 1000) / 20      // 2 minutes in ms over 20ms increments
    private const val MAX_RECORDING_SIZE = 8 * 1024 * 1024  // 8MB
    private const val BUFFER_TIMEOUT = 200L                 // 200 milliseconds
    private const val BUFFER_MAX_COUNT = 8
    private const val BITRATE = 128                         // 128 kpbs
    private const val BYTES_PER_SECOND = 16_000             // 128 kbps == 16000 bytes per second
  }

  private val logger = LoggerFactory.getLogger(this.javaClass)

  // State-licious
  private var subject: Subject<CombinedAudio>? = null
  private var subscription: Disposable? = null
  private var uuid: UUID? = null
  private var queueFile: QueueFile? = null

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
      logger.info("AFK detected, leaving '{}' voice channel in {}", voiceChannel.name, voiceChannel.guild.name)
      transaction {
        Guild.findById(voiceChannel.guild.idLong)?.settings?.defaultTextChannel
      }?.let {
        val textChannel = voiceChannel.guild.getTextChannelById(it)
        BotUtils.sendMessage(textChannel, "No audio for 2 minutes, leaving from AFK detection...")
      }

      thread(start = true) {
        BotUtils.leaveVoiceChannel(voiceChannel)
      }
    }

    return isAfk
  }

  private fun createRecording(): Disposable? {
    subject = PublishSubject.create()
    uuid = UUID.randomUUID()
    filename = "recordings/$uuid.mp3"
    queueFilename = "recordings/$uuid.queue"
    queueFile = QueueFile(File(queueFilename))
    canReceive = true

    val encoder = LameEncoder(AudioReceiveHandler.OUTPUT_FORMAT, BITRATE, LameEncoder.CHANNEL_MODE_AUTO, LameEncoder.QUALITY_HIGHEST, false)


    return subject
      ?.map { it.getAudioData(volume) }
      ?.buffer(BUFFER_TIMEOUT, TimeUnit.MILLISECONDS, BUFFER_MAX_COUNT)
      ?.flatMap({ bytesArray ->
        val baos = ByteArrayOutputStream()

        bytesArray.forEach {
          val buffer = ByteArray(it.size)
          val bytesEncoded = encoder.encodeBuffer(it, 0, it.size, buffer)
          baos.write(buffer, 0, bytesEncoded)
        }

        Observable.fromArray(baos.toByteArray())
      })
      ?.collectInto(queueFile!!, { queue, bytes ->

        while (recordingSize + bytes.size > MAX_RECORDING_SIZE) {
          recordingSize -= queue.peek()?.size ?: 0
          queue.remove()
        }
        queue.add(bytes)
        recordingSize += bytes.size
      })
      ?.subscribe({_, e ->
        e?.let {
          logger.error("An error occurred in the recording pipeline.", it)
        }
      })
  }

  fun saveRecording(voiceChannel: VoiceChannel?, textChannel: TextChannel?) {
    canReceive = false
    subscription?.dispose()

    val recording = File(filename)

    FileOutputStream(recording).use {
      queueFile?.apply {
        forEach({ stream, _ ->
          stream.transferTo(it)
        })

        clear()
        close()
        File(queueFilename).delete()
      }
    }

    val recordingSizeInMB = recording.length().toDouble() / 1024 / 1024

    logger.info("Saving audio file '{}' from {} on {} of size {} MB.",
      recording.name, voiceChannel?.name, voiceChannel?.guild?.name, recordingSizeInMB)

    uploadRecording(recording, recordingSizeInMB, voiceChannel?.name, voiceChannel?.guild?.name, textChannel)

    // Resume recording
    subscription = createRecording()
  }

  fun saveClip(seconds: Long, voiceChannel: String?, channel: TextChannel?) {
    canReceive = false
    val byteRate = BITRATE / 8

    if (seconds >= (recordingSize / 1024) / byteRate) {
      val recording = File("recordings/${UUID.randomUUID()}-clip.mp3")
      FileOutputStream(recording).use {
        queueFile?.apply {
          forEach({ stream, _ ->
            stream.transferTo(it)
          })
        }
      }

      val recordingSize = recording.length().toDouble() / 1024 / 1024

      logger.info("Saving audio file '{}' from {} on {} of size {} MB.",
          recording.name, voiceChannel, channel?.guild?.name, recordingSize)

      uploadRecording(recording, recordingSize, voiceChannel, channel?.guild?.name, channel)
    }

    canReceive = true
  }

  private fun uploadRecording(recording: File, recordingSize: Double, voiceChannelName: String?, guildName: String?, channel: TextChannel?) {
    if (recording.length() < MAX_RECORDING_SIZE) {

      val message = MessageBuilder().also {
        it.append("Unfortunately, current recordings are limited to the last 8MB recorded. Increasing limit in upcoming releases.")
        it.append("Recording for $voiceChannelName in $guildName.")
      }

      channel
        ?.sendFile(recording, recording.name, message.build())
        ?.queue(null, { BotUtils.sendMessage(channel, "I don't have permissions to send files in ${channel.name}!") })

      thread(start = true) {
        try {
          sleep((1000 * 20).toLong()) //20 second life for files sent to discord (no need to save)
        } catch (e: InterruptedException) {
          logger.error("Failed during sleep", e)
        }

        val isDeleteSuccess = recording.delete()

        logger.info("Deleting file {}...", recording.name)

        if (isDeleteSuccess)
          logger.info("Successfully deleted file {}.", recording.name)
        else
          logger.error("Could not delete file {}.", recording.name)
      }
    } else {
      BotUtils.sendMessage(channel, "Could not upload to Discord, file too large: " + recordingSize + "MB.")
    }
  }

  fun disconnect() {
    canReceive = false
    subject?.onComplete()
    subscription?.dispose()
    queueFile?.apply {
      clear()
      close()
      File(queueFilename).delete()
    }

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
