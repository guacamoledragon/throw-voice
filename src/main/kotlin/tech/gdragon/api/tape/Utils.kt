package tech.gdragon.api.tape

import com.squareup.tape.QueueFile
import java.io.File
import java.io.FileOutputStream

fun queueFileIntoMp3(q: File, mp3: File): File = queueFileIntoMp3(QueueFile(q), mp3)

/**
 * Takes the contents of the [QueueFile] into a [File], this is a stateful operation.
 */
fun queueFileIntoMp3(queueFile: QueueFile, mp3: File): File {
  FileOutputStream(mp3).use { fos ->
    queueFile.forEach{ stream, _ ->
      stream.transferTo(fos)
    }
  }
  queueFile.close()

  return mp3
}
