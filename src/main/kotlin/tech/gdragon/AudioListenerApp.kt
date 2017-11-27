package tech.gdragon

import net.dv8tion.jda.core.audio.CombinedAudio
import net.dv8tion.jda.core.entities.impl.GuildImpl
import net.dv8tion.jda.core.entities.impl.UserImpl
import net.dv8tion.jda.core.entities.impl.VoiceChannelImpl
import tech.gdragon.listener.CombinedAudioRecorderHandler
import tech.gdragon.listeners.AudioReceiveListener
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

fun main(args: Array<String>) {
  val audioSegments = generateAudioSegments()

//  legacyHandler(audioSegments)
  handler(audioSegments)
}

private fun generateAudioSegments(): List<CombinedAudio> {
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

  return combinedAudioSegments.toList()
}

private fun legacyHandler(audioSegments: List<CombinedAudio>) {
  val audioReceiveListener = AudioReceiveListener(0.8, VoiceChannelImpl(1L, GuildImpl(null, 1L)))

  audioSegments.forEach(audioReceiveListener::handleCombinedAudio)

  FileOutputStream("recordings/legacy-alone-out.pcm").use { pcm ->
    val rawVoiceData = audioReceiveListener.getUncompVoice((AudioReceiveListener.PCM_MINS * 60).toInt())
    pcm.write(rawVoiceData)

    FileOutputStream("recordings/legacy-alone-out.mp3").use { mp3 ->
      val voiceData = BotUtils.encodePcmToMp3(rawVoiceData)
      mp3.write(voiceData)
    }
  }
}

private fun handler(audioSegments: List<CombinedAudio>) {
  val audioReceiveListener = CombinedAudioRecorderHandler(0.8, VoiceChannelImpl(1L, GuildImpl(null, 1L)).also { it.name = "alone-out" })
  audioSegments.forEach(audioReceiveListener::handleCombinedAudio)

  audioReceiveListener
    .getVoiceData()
    ?.subscribe { baos ->

      FileOutputStream("recordings/alone-out.mp3").use { mp3 ->
        val rawVoiceData = baos.toByteArray()
        baos.reset()
        baos.close()
        val voiceData = BotUtils.encodePcmToMp3(rawVoiceData)

        mp3.write(voiceData)
      }
    }
}
