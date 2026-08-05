package tech.gdragon.api.tape

import com.squareup.tape.QueueFile
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.concurrent.TimeUnit

val logger = KotlinLogging.logger { }

fun queueFileIntoMp3(q: File, mp3: File): File = queueFileIntoMp3(QueueFile(q), mp3)

/**
 * Takes the contents of the [QueueFile] into a [File], this is a stateful operation.
 * When the target is an mp3, it is remuxed so it carries a valid Xing/VBR header.
 */
fun queueFileIntoMp3(queueFile: QueueFile, mp3: File): File {
  FileOutputStream(mp3).use { fos ->
    queueFile.forEach { stream, _ ->
      stream.transferTo(fos)
    }
  }
  queueFile.close()

  if (mp3.extension == "mp3" && mp3.length() > 0) {
    remuxWithXingHeader(mp3)
  }

  return mp3
}

/**
 * Remuxes the MP3 in place via ffmpeg (`-c copy -write_xing 1`) so it carries a valid
 * Xing/VBR header — required for players to compute correct duration on VBR files.
 * Audio frames are copied byte-for-byte, nothing is re-encoded.
 *
 * On any failure (ffmpeg missing, non-zero exit, timeout, or no header in the output)
 * the original file is left untouched and the failure is logged.
 */
fun remuxWithXingHeader(mp3: File, ffmpeg: String = "ffmpeg") {
  val tmp = File(mp3.parentFile, "${mp3.nameWithoutExtension}.remux.mp3")
  val ffmpegLog = File(mp3.parentFile, "${mp3.nameWithoutExtension}.remux.log")

  try {
    val process = ProcessBuilder(
      ffmpeg, "-y", "-i", mp3.absolutePath,
      "-c", "copy", "-write_xing", "1",
      "-map_metadata", "-1", "-fflags", "+bitexact",
      tmp.absolutePath
    )
      .redirectErrorStream(true)
      .redirectOutput(ffmpegLog)
      .start()

    if (!process.waitFor(60, TimeUnit.SECONDS)) {
      process.destroyForcibly()
      logger.error { "ffmpeg timed out remuxing $mp3, keeping original: ${ffmpegLog.readText().takeLast(500)}" }
      return
    }
    if (process.exitValue() != 0) {
      logger.error { "ffmpeg exit ${process.exitValue()} remuxing $mp3, keeping original: ${ffmpegLog.readText().takeLast(500)}" }
      return
    }
    if (!hasXingOrInfoHeader(tmp)) {
      logger.error { "ffmpeg output for $mp3 has no Xing/Info header, keeping original: ${ffmpegLog.readText().takeLast(500)}" }
      return
    }

    Files.move(tmp.toPath(), mp3.toPath(), StandardCopyOption.ATOMIC_MOVE)
    logger.info { "Remuxed $mp3 with Xing header" }
  } catch (e: Exception) {
    logger.error(e) { "Could not remux $mp3, keeping original: ${e.message}" }
  } finally {
    tmp.delete()
    ffmpegLog.delete()
  }
}

private fun hasXingOrInfoHeader(mp3: File): Boolean {
  val head = mp3.inputStream().use { it.readNBytes(1024) }
  if (head.size < 4) return false
  val markers = listOf("Xing".toByteArray(), "Info".toByteArray())
  return markers.any { m ->
    (0..head.size - 4).any { i ->
      head[i] == m[0] && head[i + 1] == m[1] && head[i + 2] == m[2] && head[i + 3] == m[3]
    }
  }
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
