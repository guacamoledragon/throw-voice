package tech.gdragon.api.tape

import de.sciss.jump3r.lowlevel.LameEncoder
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.longs.shouldBeGreaterThan
import java.io.File
import java.io.FileOutputStream
import javax.sound.sampled.AudioFormat

/**
 * Unit tests for [writeVbrTag].
 *
 * No mocking needed — uses a real [LameEncoder] to produce a small VBR MP3, then verifies
 * that [writeVbrTag] writes a Xing header into the first frame.
 */
class UtilsTest : FunSpec({

  // JDA's AudioReceiveHandler.OUTPUT_FORMAT: 48kHz, 16-bit, stereo, signed, big-endian
  val audioFormat = AudioFormat(48000.0f, 16, 2, true, true)

  /**
   * Encode silent PCM frames into a VBR MP3 file using a real LAME encoder.
   * Returns the encoder (still open) and the written file.
   */
  fun encodeVbrMp3(dir: File, frames: Int = 50): Pair<LameEncoder, File> {
    val encoder = LameEncoder(audioFormat, 128, LameEncoder.CHANNEL_MODE_AUTO, LameEncoder.QUALITY_HIGHEST, true)
    val mp3File = File(dir, "test-vbr.mp3")
    val mp3Buffer = ByteArray(8192)
    // 20ms frame at 48kHz 16-bit stereo = 3840 bytes
    val pcmFrame = ByteArray(3840)

    FileOutputStream(mp3File).use { fos ->
      repeat(frames) {
        val encoded = encoder.encodeBuffer(pcmFrame, 0, pcmFrame.size, mp3Buffer)
        if (encoded > 0) fos.write(mp3Buffer, 0, encoded)
      }
      val flushed = encoder.encodeFinish(mp3Buffer)
      if (flushed > 0) fos.write(mp3Buffer, 0, flushed)
    }

    return encoder to mp3File
  }

  /**
   * Check if the Xing header marker exists in the first frame of the MP3.
   * For MPEG1 stereo, the Xing tag is at offset 36 (4-byte frame header + 32-byte side info).
   */
  fun hasXingHeader(mp3: File): Boolean {
    val head = mp3.readBytes().take(200).toByteArray()
    val xing = "Xing".toByteArray()
    return (0..head.size - 4).any { i ->
      head[i] == xing[0] && head[i + 1] == xing[1] && head[i + 2] == xing[2] && head[i + 3] == xing[3]
    }
  }

  test("writeVbrTag adds Xing header to VBR MP3") {
    val dir = tempdir()
    val (encoder, mp3File) = encodeVbrMp3(dir)

    // Before: no Xing header
    hasXingHeader(mp3File) shouldBe false

    // Act
    writeVbrTag(encoder, mp3File)

    // After: Xing header present
    mp3File.length().shouldBeGreaterThan(0)
    hasXingHeader(mp3File) shouldBe true

    encoder.close()
  }

  test("writeVbrTag does not corrupt file — size stays reasonable") {
    val dir = tempdir()
    val (encoder, mp3File) = encodeVbrMp3(dir)
    val sizeBefore = mp3File.length()

    writeVbrTag(encoder, mp3File)

    // putVbrTag overwrites first frame in-place, file size should not change drastically
    val sizeAfter = mp3File.length()
    sizeAfter shouldBe sizeBefore

    encoder.close()
  }
})
