package tech.gdragon.koin

import org.koin.core.KoinApplication
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.logger.Level
import java.io.File
import java.io.FileInputStream
import java.util.*

/**
 * Generate a hashmap from a Properties object.
 */
private fun fromProperties(properties: Properties): Map<String, Any> = buildMap {
  properties.forEach { property ->
    put(property.key as String, property.value)
  }
}

@OptIn(KoinInternalApi::class)
fun KoinApplication.overrideFileProperties(overrideFilename: String): KoinApplication {
  val overrideFile = File(overrideFilename)

  if (overrideFile.exists()) {
    FileInputStream(overrideFile).use { inputStream ->
      val overrideProperties = Properties().also {
        it.load(inputStream)
      }

      properties(fromProperties(overrideProperties))
      if (koin.logger.isAt(Level.INFO)) {
        koin.logger.info("loaded properties from file:'${overrideFile.canonicalPath}'")
      }
    }
  } else {
    koin.logger.error("Override file $overrideFilename doesn't exist, please double check the file path.")
  }

  return this
}
