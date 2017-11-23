package tech.gdragon.listener.rx

import net.dv8tion.jda.core.audio.AudioReceiveHandler
import net.dv8tion.jda.core.audio.CombinedAudio
import net.dv8tion.jda.core.audio.UserAudio
import net.dv8tion.jda.core.entities.VoiceChannel

class AudioReceive(val volume: Double, val voiceChannel: VoiceChannel) : AudioReceiveHandler {
  /**
   * If this method returns true, then JDA will provide audio data to the [.handleUserAudio] method.
   *
   * @return If true, JDA enables subsystems to provide user specific audio data.
   */
  override fun canReceiveUser(): Boolean = false

  /**
   * If [.canReceiveCombined] returns true, JDA will provide a [CombinedAudio][net.dv8tion.jda.core.audio.CombinedAudio]
   * object to this method **every 20 milliseconds**. The data provided by CombinedAudio is all audio that occurred
   * during the 20 millisecond period mixed together into a single 20 millisecond packet. If no users spoke, this method
   * will still be provided with a CombinedAudio object containing 20 milliseconds of silence and
   * [CombinedAudio.getUsers]'s list will be empty.
   *
   *
   * The main use of this method is if you are wanting to record audio. Because it automatically combines audio and
   * maintains timeline (no gaps in audio due to silence) it is an incredible resource for audio recording.
   *
   *
   * If you are wanting to do audio processing (voice recognition) or you only want to deal with a single user's audio,
   * please consider [.handleUserAudio].
   *
   *
   * Output audio format: 48KHz 16bit stereo signed BigEndian PCM
   * <br></br>and is defined by: [AudioRecieveHandler.OUTPUT_FORMAT][net.dv8tion.jda.core.audio.AudioReceiveHandler.OUTPUT_FORMAT]
   *
   * @param  combinedAudio
   * The combined audio data.
   */
  override fun handleCombinedAudio(combinedAudio: CombinedAudio) {
    // NOTE: Each segment of audio data is 3840 bytes long
    // NOTE: 48KHz 16bit stereo signed BigEndian PCM
    combinedAudio.getAudioData(volume)
  }

  /**
   * If [.canReceiveUser] returns true, JDA will provide a [UserAudio][net.dv8tion.jda.core.audio.UserAudio]
   * object to this method **every time the user speaks.** Continuing with the last statement: This method is only fired
   * when discord provides us audio data which is very different from the scheduled firing time of
   * [.handleCombinedAudio].
   *
   *
   * The [UserAudio][net.dv8tion.jda.core.audio.UserAudio] object provided to this method will contain the
   * [User][net.dv8tion.jda.core.entities.User] that spoke along with **only** the audio data sent by the specific user.
   *
   *
   * The main use of this method is for listening to specific users. Whether that is for audio recording,
   * custom mixing (possibly for user muting), or even voice recognition, this is the method you will want.
   *
   *
   * If you are wanting to do audio recording, please consider [.handleCombinedAudio] as it was created
   * just for that reason.
   *
   *
   * Output audio format: 48KHz 16bit stereo signed BigEndian PCM
   * <br></br>and is defined by: [AudioRecieveHandler.OUTPUT_FORMAT][net.dv8tion.jda.core.audio.AudioReceiveHandler.OUTPUT_FORMAT]
   *
   * @param  userAudio
   * The user audio data
   */
  override fun handleUserAudio(userAudio: UserAudio?) {
    TODO("Only handles combined audio")
  }

  /**
   * If this method returns true, then JDA will generate combined audio data and provide it to the handler.
   * <br></br>**Only enable if you specifically want combined audio because combining audio is costly if unused.**
   *
   * @return If true, JDA enables subsystems to combine all user audio into a single provided data packet.
   */
  override fun canReceiveCombined(): Boolean = true
}
