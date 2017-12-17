package tech.gdragon

import de.sciss.jump3r.lowlevel.LameEncoder
import io.reactivex.Observable
import net.dv8tion.jda.core.audio.AudioReceiveHandler
import net.dv8tion.jda.core.audio.CombinedAudio
import net.dv8tion.jda.core.entities.impl.GuildImpl
import net.dv8tion.jda.core.entities.impl.UserImpl
import net.dv8tion.jda.core.entities.impl.VoiceChannelImpl
import tech.gdragon.listener.CombinedAudioRecorderHandler
import tech.gdragon.listeners.AudioReceiveListener
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
  val audioSegments = generateAudioSegments()

  /**
   * every 20 ms we get a 3840 byte sample
   * LameEncoder.DEFAULT_PCM_BUFFER_SIZE = 2048 * 16 = 32768
   * It takes 8.5 messages to fill the PCM buffer, let's round down 8
   * In time this is 160 ms, but let's give it an over head of 40 ms to 200ms
   */
//  legacyHandler(audioSegments)
//  handler(audioSegments)
  val encoder = LameEncoder(AudioReceiveHandler.OUTPUT_FORMAT, 128, LameEncoder.CHANNEL_MODE_AUTO, LameEncoder.QUALITY_HIGHEST, false)


  Observable.fromIterable(audioSegments)
    .map { it.getAudioData(1.0) }
    .buffer(200, TimeUnit.MILLISECONDS, 8)
    .collect(::ByteArrayOutputStream, { baos, bytesArray ->
      val x = ByteBuffer.allocate(10)
      val y = ByteBuffer.allocate(10)
      x.put(y)
      bytesArray.forEach {
        val buffer = ByteArray(it.size)
        val bytesEncoded = encoder.encodeBuffer(it, 0, it.size, buffer)
        println("bytesEncoded = ${bytesEncoded}")
        baos.write(buffer, 0, bytesEncoded)
      }
    })
    .subscribe { baos ->
      FileOutputStream("recordings/alone-observable.mp3").use {
        it.write(baos.toByteArray())
        baos.close()
      }
    }
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
//  val audioReceiveListener = CombinedAudioRecorderHandler(0.8, VoiceChannelImpl(1L, GuildImpl(null, 1L)).also { it.name = "alone-out" })
//  audioSegments.forEach(audioReceiveListener::handleCombinedAudio)

/*  audioReceiveListener
    .getVoiceData()
    ?.subscribe { baos ->

      FileOutputStream("recordings/alone-out.mp3").use { mp3 ->
        val rawVoiceData = baos.toByteArray()
        baos.reset()
        baos.close()
        val voiceData = BotUtils.encodePcmToMp3(rawVoiceData)

        mp3.write(voiceData)
      }
    }*/
}
