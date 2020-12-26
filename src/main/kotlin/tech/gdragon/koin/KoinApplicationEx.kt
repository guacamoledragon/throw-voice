package tech.gdragon.koin

import com.google.common.collect.Maps
import org.koin.core.KoinApplication
import org.koin.core.logger.Level
import java.io.File
import java.io.FileInputStream
import java.util.*

fun KoinApplication.overrideFileProperties(overrideFilename: String): KoinApplication {
  val overrideFile = File(overrideFilename)

  if (overrideFile.exists()) {
    FileInputStream(overrideFile).use { inputStream ->
      val overrideProperties = Properties().also {
        it.load(inputStream)
      }

      properties(Maps.fromProperties(overrideProperties))
      if (koin.logger.isAt(Level.INFO)) {
        koin.logger.info("loaded properties from file:'${overrideFile.canonicalPath}'")
      }
    }
  } else {
    koin.logger.error("Override file $overrideFilename doesn't exist, please double check the file path.")
  }

  return this
}
