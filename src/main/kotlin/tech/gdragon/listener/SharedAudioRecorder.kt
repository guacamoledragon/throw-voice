package tech.gdragon.listener

import com.squareup.tape.QueueFile
import io.github.oshai.kotlinlogging.withLoggingContext
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import tech.gdragon.db.now
import java.io.File
import kotlin.concurrent.thread

/**
 * Shared server audio recorder with AFK detection, size limits, and Discord upload options.
 */
class SharedAudioRecorder(
  volume: Double,
  voiceChannel: AudioChannel,
  messageChannel: MessageChannel,
  uploadWaitTimeout: java.time.Duration = DEFAULT_UPLOAD_WAIT,
  drainTimeout: java.time.Duration = DEFAULT_DRAIN_WAIT
) : BaseAudioRecorder(volume, voiceChannel, messageChannel, uploadWaitTimeout, drainTimeout) {

  companion object {
    private const val AFK_MINUTES = 2
    private const val AFK_LIMIT = (AFK_MINUTES * 60 * 1000) / 20          // 2 minutes in 20ms increments
    private const val MAX_RECORDING_MB = 256L
    private const val MAX_RECORDING_SIZE = MAX_RECORDING_MB * 1024 * 1024 // 256MB
  }

  private val afkCounter = java.util.concurrent.atomic.AtomicInteger(0)
  private val afkTriggered = java.util.concurrent.atomic.AtomicBoolean(false)
  private val recordingSize = java.util.concurrent.atomic.AtomicLong(0L)
  private val limitWarning = java.util.concurrent.atomic.AtomicBoolean(false)

  override fun shouldProcessAudio(audioData: AudioData): Boolean {
    return !checkAfkStatus(audioData.userCount)
  }

  private fun checkAfkStatus(userCount: Int): Boolean {
    if (userCount == 0) {
      afkCounter.incrementAndGet()
    } else {
      afkCounter.set(0)
      afkTriggered.set(false)
    }

    val isAfk = afkCounter.get() >= AFK_LIMIT
    if (isAfk && afkTriggered.compareAndSet(false, true)) {
      withLoggingContext("guild" to voiceChannel.guild.name, "voice-channel" to voiceChannel.name) {
        logger.debug { "AFK detected." }
      }

      tech.gdragon.BotUtils.sendMessage(
        messageChannel,
        ":sleeping: _No audio detected in the last **$AFK_MINUTES** minutes, leaving **<#${voiceChannel.id}>**._"
      )

      thread {
        val save = pawa.autoSave(voiceChannel.guild.idLong)
        tech.gdragon.BotUtils.leaveVoiceChannel(voiceChannel, messageChannel, save)
      }
    }

    return isAfk
  }

  override fun handleSizeLimit(queue: QueueFile, dataToWrite: ByteArray) {
    // Remove old data if we're over the limit
    if (recordingSize.get() + dataToWrite.size > MAX_RECORDING_SIZE) {
      queue.peek()?.let { oldData ->
        queue.remove()
        recordingSize.addAndGet(-oldData.size.toLong())
      }
    }
  }

  override fun onAudioDataWritten(bytesWritten: Int) {
    val newSize = recordingSize.addAndGet(bytesWritten.toLong())

    // Warning for approaching limit
    if (!limitWarning.get()) {
      val percentage = newSize * 100 / MAX_RECORDING_SIZE
      if (percentage >= 90) {
        limitWarning.set(true)
        tech.gdragon.BotUtils.sendMessage(
          messageChannel,
          ":warning: _You've reached $percentage% of your recording limit, please save to start a new session._"
        )
      }
    }
  }

  override fun uploadRecording(recordingFile: File, voiceChannel: AudioChannel, messageChannel: MessageChannel) {
    try {
      val filename = recordingFile.name

      // Discord attachment URLs expire, so the datastore URL is the one we store.
      // A null attachment means Discord did NOT take the file: size, guild upload limit
      // (400001), or missing permissions.
      val attachment = try {
        uploadAttachment(messageChannel, recordingFile, filename)?.attachments?.first()
      } catch (e: Exception) {
        logger.warn(e) { "Discord attachment upload failed: $session" }
        null
      }

      val result = try {
        datastore.upload("${voiceChannel.guild.id}/$filename", recordingFile)
      } catch (e: Exception) {
        if (attachment == null) throw e
        logger.warn(e) { "Datastore upload failed, storing the Discord URL: $session" }
        null
      }

      val storedUrl = result?.url ?: attachment!!.proxyUrl

      transaction {
        recordingRecord?.apply {
          size = result?.size ?: recordingFile.length()
          modifiedOn = result?.timestamp ?: now()
          url = storedUrl
          duration = this@SharedAudioRecorder.duration
        }
      }

      reportUploaded()

      // Cleanup local file
      if (recordingFile.delete()) {
        logger.info { "Successfully deleted local file ${recordingFile.name}" }
      } else {
        logger.warn { "Could not delete local file ${recordingFile.name}" }
      }

    } catch (e: Exception) {
      logger.error(e) { "Error uploading recording: $session" }
      val errorMessage =
        """|:no_entry_sign: _Error uploading recording, please visit support server and provide Session ID._
                                 |_Session ID: `$session`_""".trimMargin()
      reportFailure(messageChannel, errorMessage)
    }
  }
}
