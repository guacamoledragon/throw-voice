package tech.gdragon.listener

import mu.KotlinLogging
import net.dv8tion.jda.api.audio.AudioSendHandler
import java.nio.ByteBuffer

class SilenceAudioSendHandler : AudioSendHandler {
  private val logger = KotlinLogging.logger { }
  var canProvide = true
    set(value) {
      logger.debug { "canProvide toggling from $canProvide to $value" }
      field = value
    }

  override fun provide20MsAudio(): ByteBuffer? {
    val silence = arrayOf(0xF8.toByte(), 0xFF.toByte(), 0xFE.toByte())
    return ByteBuffer.wrap(silence.toByteArray())
  }

  override fun canProvide(): Boolean {
    return canProvide
  }

  override fun isOpus(): Boolean {
    return true
  }
}
