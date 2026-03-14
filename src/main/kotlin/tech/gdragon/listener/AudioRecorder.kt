package tech.gdragon.listener

import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import tech.gdragon.db.dao.Recording
import java.util.concurrent.Semaphore

/**
 * Common interface for audio recorder implementations.
 *
 * Both [CombinedAudioRecorderHandler] (legacy RxJava) and [BaseAudioRecorder] (queue-based)
 * implement this so callers like [tech.gdragon.BotUtils] can work with either without hard casts.
 */
interface AudioRecorder {
  val session: String
  val recording: Recording?
  var volume: Double

  fun saveRecording(
    voiceChannel: AudioChannel,
    messageChannel: MessageChannel
  ): Pair<Recording?, Semaphore?>

  /**
   * Clean up resources and wait for any pending operations to complete.
   *
   * @param save whether a save was performed prior to disconnect
   * @param recording the recording entity from [saveRecording], or null if not saved
   * @param recordingLock the semaphore from [saveRecording], or null if not saved
   */
  fun disconnect(save: Boolean, recording: Recording?, recordingLock: Semaphore?)

  fun silenceUser(userId: Long)
}
