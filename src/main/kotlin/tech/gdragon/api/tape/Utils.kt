package tech.gdragon.api.tape

import com.squareup.tape.QueueFile
import java.io.File
import java.io.FileOutputStream

fun queueFileIntoMp3(q: File, mp3: File): File {
  val queueFile = QueueFile(q)

  FileOutputStream(mp3).use { fos ->
    queueFile.forEach{ stream, _ ->
      stream.transferTo(fos)
    }
  }

  return mp3
}
