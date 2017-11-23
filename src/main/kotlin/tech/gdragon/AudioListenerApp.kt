package tech.gdragon

import net.dv8tion.jda.core.audio.CombinedAudio
import net.dv8tion.jda.core.entities.impl.GuildImpl
import net.dv8tion.jda.core.entities.impl.UserImpl
import net.dv8tion.jda.core.entities.impl.VoiceChannelImpl
import tech.gdragon.listeners.AudioReceiveListener
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

fun main(args: Array<String>) {
  val combinedAudioSegments = mutableListOf<CombinedAudio>()

  // Recreate combined audio segment stream
  FileInputStream("recordings/alone.pcm").use {
    val msgSizeBytes = 3840

    while (it.available() != 0) {
      val bytes = ByteArray(msgSizeBytes)
      val shorts = ShortArray(msgSizeBytes / 2)
      it.readNBytes(bytes, 0, msgSizeBytes)

      ByteBuffer.wrap(bytes).asShortBuffer().get(shorts)
      val combinedAudio = CombinedAudio(listOf(UserImpl(1L, null)), shorts)

      combinedAudioSegments.add(combinedAudio)
    }
  }

  val audioReceiveListener = AudioReceiveListener(0.8, VoiceChannelImpl(1L, GuildImpl(null, 1L)))

  combinedAudioSegments.forEach(audioReceiveListener::handleCombinedAudio)

  FileOutputStream("recordings/alone-out.pcm").use { pcm ->
    val rawVoiceData = audioReceiveListener.getUncompVoice((AudioReceiveListener.PCM_MINS * 60).toInt())
    pcm.write(rawVoiceData)

    FileOutputStream("recordings/alone-out.mp3").use { mp3 ->
      val voiceData = BotUtils.encodePcmToMp3(rawVoiceData)
      mp3.write(voiceData)
    }
  }
}
