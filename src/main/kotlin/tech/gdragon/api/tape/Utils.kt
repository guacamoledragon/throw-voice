package tech.gdragon.api.tape

import com.squareup.tape.QueueFile
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.io.FileOutputStream

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
