package tech.gdragon.api.commands

import tech.gdragon.db.dao.Recording
import java.io.File
import java.nio.file.Paths

fun sanitizeFilename(input: String): String {
  // Remove any directory traversal attempts
  val filename = input.replace(Regex("[.]{2,}"), "")

  // Remove any characters that aren't alphanumeric, underscore, hyphen, or dot
  return filename.replace(Regex("[^a-zA-Z0-9_.-]"), "")
}

fun createSafeFile(baseDir: String, filename: String): File {
  val sanitizedFilename = sanitizeFilename(filename)
  val basePath = Paths.get(baseDir).normalize()
  val safePath = basePath.resolve(sanitizedFilename)

  // If the resolved path is not within the base directory, return a non-existent file
  return if (safePath.startsWith(basePath)) {
    safePath.toFile()
  } else {
    File(baseDir, "invalid_${System.currentTimeMillis()}")
  }
}

data class RecoverResult(val id: String, val recording: Recording?)
