package tech.gdragon.db

import org.jetbrains.exposed.sql.TextColumnType
import tech.gdragon.i18n.Lang

class LanguageColumnType : TextColumnType() {
  override fun valueFromDB(value: Any): Any = when (value) {
    is ByteArray -> Lang.valueOf(String(value))
    else -> Lang.valueOf(value as String)
  }

  override fun valueToDB(value: Any?): Any? {
    if (value is Lang) {
      return value.toString()
    }
    return value
  }
}
