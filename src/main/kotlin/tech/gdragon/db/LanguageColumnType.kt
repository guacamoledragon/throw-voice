package tech.gdragon.db

import org.jetbrains.exposed.sql.TextColumnType
import tech.gdragon.i18n.Lang

class LanguageColumnType : TextColumnType() {
  override fun valueFromDB(value: Any): Any = when (value) {
    is java.sql.Clob -> Lang.valueOf(value.characterStream.readText())
    is ByteArray -> Lang.valueOf(String(value))
    is Lang -> value
    else -> Lang.valueOf(value as String)
  }

  override fun valueToDB(value: Any?): Any? {
    if (value is Lang) {
      return value.toString()
    }
    return value
  }
}
