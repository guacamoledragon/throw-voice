package tech.gdragon.listener

import com.squareup.tape.QueueFile
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.db.dtf
import tech.gdragon.db.nowUTC
import java.io.File

/**
 * Standalone audio recorder with no size limits, AFK detection, or shared server features.
 */
class StandaloneAudioRecorder(volume: Double, voiceChannel: AudioChannel, messageChannel: MessageChannel) :
  BaseAudioRecorder(volume, voiceChannel, messageChannel) {

  /**
   * Always true, skip AFK detection
   */
  override fun shouldProcessAudio(audioData: AudioData): Boolean = true

  /**
   * No-op, no size limits in standalone mode
   */
  override fun handleSizeLimit(queue: QueueFile, dataToWrite: ByteArray) {}

  /**
   * No-op, no size tracking needed in standalone mode
   */
  override fun onAudioDataWritten(bytesWritten: Int) {}

  override fun uploadRecording(recordingFile: File, voiceChannel: AudioChannel, messageChannel: MessageChannel) {
    try {
      val filename = "${voiceChannel.name}-${dtf.print(nowUTC())}.$fileFormat"
      val recordingKey = "${voiceChannel.guild.id}/$filename"
      val result = datastore.upload(recordingKey, recordingFile)

      // Update database record
      transaction {
        recordingRecord?.apply {
          size = result.size
          modifiedOn = result.timestamp
          url = result.url
          duration = this@StandaloneAudioRecorder.duration
        }
      }

      uploadAttachment(messageChannel, recordingFile, filename)

      // Send simple message with direct file URL
      val message = """|:microphone2: **Recording for <#${voiceChannel.id}> has been uploaded!**
                       |`${result.url}`
                       |""".trimMargin()

      tech.gdragon.BotUtils.sendMessage(messageChannel, message)

      // Cleanup local file
      if (recordingFile.delete()) {
        logger.info { "Successfully deleted local file ${recordingFile.name}" }
      } else {
        logger.warn { "Could not delete local file ${recordingFile.name}" }
      }

    } catch (e: Exception) {
      logger.error(e) { "Error uploading recording: $session" }
      val errorMessage = """|:no_entry_sign: _Error uploading recording, please visit support server and provide Session ID._
                            |_Session ID: `$session`_
                            |""".trimMargin()
      tech.gdragon.BotUtils.sendMessage(messageChannel, errorMessage)
    }
  }
}
