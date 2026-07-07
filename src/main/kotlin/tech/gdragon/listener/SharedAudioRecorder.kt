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
  uploadWaitTimeout: java.time.Duration = DEFAULT_UPLOAD_WAIT
) : BaseAudioRecorder(volume, voiceChannel, messageChannel, uploadWaitTimeout) {

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

      // Try Discord first — attachments are free storage that stays valid for the lifetime
      // of the server. A null attachment means Discord did NOT take the file, whether by
      // size (uploadAttachment's gate), guild upload limit (400001), or missing permissions;
      // in all of those cases fall back to the datastore so the recording is never stranded.
      val attachment = try {
        uploadAttachment(messageChannel, recordingFile, filename)?.attachments?.first()
      } catch (e: Exception) {
        logger.warn(e) { "Discord attachment upload failed, falling back to datastore: $session" }
        null
      }

      if (attachment != null) {
        // Discord-only upload
        transaction {
          recordingRecord?.apply {
            size = recordingFile.length()
            modifiedOn = now()
            url = attachment.proxyUrl
            duration = this@SharedAudioRecorder.duration
          }
        }

        val appUrl = pawa.config.appUrl
        val recordingUrl = if (appUrl.startsWith("discord://")) {
          attachment.proxyUrl
        } else {
          "$appUrl/v1/recordings?guild=${voiceChannel.guild.idLong}&session-id=$session"
        }
        val message = """|:microphone2: **Recording for <#${voiceChannel.id}> has been uploaded!**
                                |$recordingUrl""".trimMargin()

        tech.gdragon.BotUtils.sendMessage(messageChannel, message)
      } else {
        // Large files, or Discord upload failed → upload to datastore
        val recordingKey = "${voiceChannel.guild.id}/$filename"
        val result = datastore.upload(recordingKey, recordingFile)

        transaction {
          recordingRecord?.apply {
            size = result.size
            modifiedOn = result.timestamp
            url = result.url
            duration = this@SharedAudioRecorder.duration
          }
        }

        val appUrl = pawa.config.appUrl
        val recordingUrl = if (appUrl.startsWith("discord://")) {
          result.url
        } else {
          "$appUrl/v1/recordings?guild=${voiceChannel.guild.idLong}&session-id=$session"
        }

        val message = """|:microphone2: **Recording for <#${voiceChannel.id}> has been uploaded!**
                                |$recordingUrl
                                |
                                |_Recording will only be available for 24hrs_""".trimMargin()

        tech.gdragon.BotUtils.sendMessage(messageChannel, message)
      }

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
      tech.gdragon.BotUtils.sendMessage(messageChannel, errorMessage)
    }
  }
}
