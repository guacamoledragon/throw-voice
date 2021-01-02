package tech.gdragon.koin

import org.koin.core.Koin
import org.koin.core.error.MissingPropertyException

fun Koin.getStringProperty(key: String): String = getProperty(key)
  ?: throw MissingPropertyException("Property '$key' not found")

fun Koin.getBooleanProperty(key: String): Boolean = getProperty<String>(key)?.toBoolean()
  ?: throw MissingPropertyException("Property '$key' not found")
