package tech.gdragon.api.tape

import de.sciss.jump3r.lowlevel.LameEncoder
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import java.io.FileOutputStream
import javax.sound.sampled.AudioFormat

/**
 * Unit tests for the mp3 frame walk.
 *
 * No mocking — every fixture is encoded at runtime by a real LAME encoder, then damaged
 * in the specific way production files are damaged (see work item #96).
 */
class Mp3FrameWalkTest : FunSpec({

  // JDA's AudioReceiveHandler.OUTPUT_FORMAT: 48kHz, 16-bit, stereo, signed, big-endian
  val audioFormat = AudioFormat(48000.0f, 16, 2, true, true)

  /** Encode silent PCM frames into a VBR MP3 file, exactly as the recorder does. */
  fun encodeMp3(dir: File, name: String = "clean.mp3", frames: Int = 50, vbr: Boolean = true): File {
    val encoder = LameEncoder(audioFormat, 128, LameEncoder.CHANNEL_MODE_AUTO, LameEncoder.QUALITY_HIGHEST, vbr)
    val mp3File = File(dir, name)
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
    encoder.close()

    return mp3File
  }

  /** Drop [bytes] from the end of [src], the damage signature from work item #96. */
  fun clipTail(src: File, dest: File, bytes: Int): File {
    val data = src.readBytes()
    dest.writeBytes(data.copyOf(data.size - bytes))
    return dest
  }

  test("walk of a clean mp3 reports no shortfall") {
    val mp3 = encodeMp3(tempdir())

    val walk = walkMp3Frames(mp3)!!

    walk.shortfall shouldBe 0L
    walk.frameCount shouldBeGreaterThan 0
    walk.lastFrameEnd shouldBe mp3.length()
  }

  test("walk of an mp3 clipped by 4 bytes reports a shortfall of 4") {
    val dir = tempdir()
    val clean = encodeMp3(dir)
    val clipped = clipTail(clean, File(dir, "clipped.mp3"), 4)

    val walk = walkMp3Frames(clipped)!!

    walk.shortfall shouldBe 4L
  }

  test("trim removes the incomplete frame and leaves the file on a frame boundary") {
    val dir = tempdir()
    val clean = encodeMp3(dir)
    val cleanWalk = walkMp3Frames(clean)!!
    val clipped = clipTail(clean, File(dir, "clipped.mp3"), 4)

    val removed = trimIncompleteTrailingFrame(clipped)

    removed shouldBeGreaterThan 0L
    val after = walkMp3Frames(clipped)!!
    after.shortfall shouldBe 0L
    after.lastFrameEnd shouldBe clipped.length()
    after.frameCount shouldBe cleanWalk.frameCount - 1
  }

  test("trim leaves a clean mp3 byte for byte identical") {
    val dir = tempdir()
    val mp3 = encodeMp3(dir)
    val before = mp3.readBytes()

    val removed = trimIncompleteTrailingFrame(mp3)

    removed shouldBe 0L
    mp3.readBytes() shouldBe before
  }

  test("trim keeps an ID3v1 tag at the end of the file") {
    val dir = tempdir()
    val mp3 = encodeMp3(dir)
    // Pawa.recoverRecording remuxes an mp3 that addCommentToMp3 already tagged.
    mp3.appendBytes("TAG".toByteArray() + ByteArray(125))
    val before = mp3.readBytes()

    trimIncompleteTrailingFrame(mp3) shouldBe 0L

    mp3.readBytes() shouldBe before
  }

  test("walk finds the first frame after an ID3v2 tag") {
    val dir = tempdir()
    val clean = encodeMp3(dir)
    // ID3v2.4 header: "ID3", version, flags, then a 4-byte syncsafe size of 32
    val tag = byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x20) + ByteArray(32)
    val tagged = File(dir, "tagged.mp3")
    tagged.writeBytes(tag + clean.readBytes())

    val walk = walkMp3Frames(tagged)!!

    walk.audioStart shouldBe 42L
    walk.shortfall shouldBe 0L
    walk.frameCount shouldBe walkMp3Frames(clean)!!.frameCount
  }

  test("walk resynchronizes when the head of the file is cut mid frame") {
    val dir = tempdir()
    val clean = encodeMp3(dir)
    // SharedAudioRecorder.handleSizeLimit drops whole queue entries off the head,
    // so the surviving file starts in the middle of a frame.
    val headless = File(dir, "headless.mp3")
    headless.writeBytes(clean.readBytes().drop(500).toByteArray())

    val walk = walkMp3Frames(headless)!!

    walk.shortfall shouldBe 0L
    walk.lastFrameEnd shouldBe headless.length()
  }

  test("walk returns null when the file holds no frame header") {
    val dir = tempdir()
    val junk = File(dir, "junk.mp3")
    junk.writeBytes(ByteArray(4096))

    walkMp3Frames(junk).shouldBeNull()
  }

  test("trim does nothing when the file holds no frame header") {
    val dir = tempdir()
    val junk = File(dir, "junk.mp3")
    junk.writeBytes(ByteArray(4096))

    trimIncompleteTrailingFrame(junk) shouldBe 0L
    junk.length() shouldBe 4096L
  }

  test("walk fields carry the Honeycomb column names as strings") {
    val walk = Mp3Walk(audioStart = 0L, frameCount = 22941, lastFrameEnd = 7_801_100L, shortfall = 4L, resyncCount = 0)

    val fields = walk.loggingFields(stage = "pre-remux", fileBytes = 7_801_240L)

    fields shouldBe mapOf(
      "audio.mp3.stage" to "pre-remux",
      "audio.mp3.tail.shortfall" to "4",
      "audio.mp3.frame.count" to "22941",
      "audio.mp3.resync.count" to "0",
      "audio.mp3.file.bytes" to "7801240"
    )
  }

  test("trim repairs every clip size that damages the last frame") {
    val dir = tempdir()
    val clean = encodeMp3(dir)
    val cleanBytes = clean.readBytes()

    // Locate the last frame: clip one byte, trim, and the survivor ends where that frame starts.
    val probe = clipTail(clean, File(dir, "probe.mp3"), 1)
    trimIncompleteTrailingFrame(probe)
    val lastFrameLength = cleanBytes.size - probe.length().toInt()

    lastFrameLength shouldBeGreaterThan 0

    for (clip in 1 until lastFrameLength) {
      val clipped = File(dir, "clip-$clip.mp3")
      clipped.writeBytes(cleanBytes.copyOf(cleanBytes.size - clip))

      trimIncompleteTrailingFrame(clipped)

      val after = walkMp3Frames(clipped)!!
      withClue("clip of $clip bytes") {
        after.shortfall shouldBe 0L
        after.lastFrameEnd shouldBe clipped.length()
      }
    }
  }
})
