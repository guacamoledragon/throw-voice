@file:JvmName("Queue2Mp3")

package tech.gdragon

import com.squareup.tape.QueueFile
import java.io.File
import java.io.FileOutputStream

fun queue2mp3(qFile: String) {
  println("qFile = $qFile")
  val recording = File(qFile.replace("queue", "mp3"))
  val queueFile = QueueFile(File(qFile))

  FileOutputStream(recording).use { fos ->
    queueFile.forEach { stream, _ ->
      stream.transferTo(fos)
    }
  }
}

fun main(args: Array<String>) {
  queue2mp3(args.first())
}
