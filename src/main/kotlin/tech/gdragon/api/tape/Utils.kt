package tech.gdragon.api.tape

import com.squareup.tape.QueueFile
import de.sciss.jump3r.lowlevel.LameEncoder
import de.sciss.jump3r.mp3.LameGlobalFlags
import de.sciss.jump3r.mp3.VBRTag
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.time.Duration

val logger = KotlinLogging.logger { }

fun queueFileIntoMp3(q: File, mp3: File): File = queueFileIntoMp3(QueueFile(q), mp3)

/**
 * Takes the contents of the [QueueFile] into a [File], this is a stateful operation.
 */
fun queueFileIntoMp3(queueFile: QueueFile, mp3: File): File {
  FileOutputStream(mp3).use { fos ->
    queueFile.forEach { stream, _ ->
      stream.transferTo(fos)
    }
  }
  queueFile.close()

  return mp3
}

fun addCommentToMp3(mp3: File, comment: String?) {
  if (comment.isNullOrBlank()) logger.info {
    "Skip tagging mp3, comment is empty."
  } else {
    try {
      val audioFile = AudioFileIO.read(mp3) as MP3File
      val tag = audioFile.tagAndConvertOrCreateAndSetDefault
      tag.setField(FieldKey.COMMENT, comment)
      audioFile.commit()
    } catch (e: InvalidAudioFrameException) {
      logger.error(e) {
        "Error tagging $mp3: ${e.message}"
      }
    }
  }
}

/**
 * Writes a Xing/VBR header to the first frame of the MP3 file using LAME's internal VBR data.
 * Uses reflection to access [LameEncoder]'s private `gfp` and package-private `vbr` fields.
 * This header is required for browsers and audio libraries to compute accurate duration for VBR files.
 */
fun writeVbrTag(encoder: LameEncoder, mp3: File) {
  val gfpField = LameEncoder::class.java.getDeclaredField("gfp")
  gfpField.isAccessible = true
  val gfp = gfpField.get(encoder) as LameGlobalFlags

  val vbrField = LameEncoder::class.java.getDeclaredField("vbr")
  vbrField.isAccessible = true
  val vbrTag = vbrField.get(encoder) as VBRTag

  RandomAccessFile(mp3, "rw").use { raf ->
    vbrTag.putVbrTag(gfp, raf)
  }

  logger.info { "Wrote VBR/Xing header to $mp3" }
}

fun extractDuration(mp3: File): Duration =
  try {
    val audioFile = AudioFileIO.read(mp3) as MP3File
    Duration.ofSeconds(audioFile.mP3AudioHeader.trackLength.toLong())
  } catch (e: InvalidAudioFrameException) {
    logger.warn {
      "Could not extract duration from audio header."
    }
    Duration.ofSeconds(0L)
  }
