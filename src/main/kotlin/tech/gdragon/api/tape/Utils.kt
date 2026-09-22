package tech.gdragon.api.tape

import com.squareup.tape.QueueFile
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
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

/**
 * Remuxes the MP3 in place via ffmpeg (`-c copy -write_xing 1`) so it carries a valid
 * Xing/VBR header — required for players to compute correct duration on VBR files.
 * Audio frames are copied byte-for-byte, nothing is re-encoded.
 *
 * On any failure (ffmpeg missing, non-zero exit, timeout, or no header in the output)
 * the original file is left untouched and the failure is logged.
 *
 * Known ceiling: the Xing TOC is a fixed 100-entry table, so on long VBR recordings
 * seeking stays coarse (~34 s buckets on an hour-long file) even with a correct header —
 * duration is exact, seek position is not. Only a container with a per-sample index
 * (e.g. m4a) fixes seek accuracy.
 */
fun remuxWithXingHeader(mp3: File) {
  if (mp3.length() <= 0) return

  logTailCheck(mp3, "pre-remux")

  val ffmpeg = "ffmpeg"
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

    logTailCheck(tmp, "post-remux")

    Files.move(tmp.toPath(), mp3.toPath(), StandardCopyOption.ATOMIC_MOVE)
    logger.info { "Remuxed $mp3 with Xing header" }
  } catch (e: Exception) {
    logger.error(e) { "Could not remux $mp3, keeping original: ${e.message}" }
  } finally {
    tmp.delete()
    ffmpegLog.delete()
  }
}

/**
 * Walks the frame headers and records the result as MDC fields, one event per file per stage.
 *
 * Measurement only — nothing is repaired here yet (work item #96). Clean files are logged too,
 * because they are the denominator: a single Honeycomb query over
 * `message starts-with "Mp3 tail check"` grouped by `audio.mp3.tail.shortfall` gives the rate.
 */
private fun logTailCheck(mp3: File, stage: String) {
  val walk = walkMp3Frames(mp3)

  if (walk == null) {
    logger.warn { "Mp3 tail check found no frame header in $mp3" }
    return
  }

  withLoggingContext(walk.loggingFields(stage, mp3.length())) {
    if (walk.shortfall > 0L) {
      logger.warn { "Mp3 tail check found an incomplete final frame in $mp3" }
    } else {
      logger.info { "Mp3 tail check" }
    }
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
