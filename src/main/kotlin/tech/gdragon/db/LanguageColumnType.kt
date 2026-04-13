package tech.gdragon.db

import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import tech.gdragon.i18n.Lang

class LanguageColumnType : ColumnType<Lang>() {

  override fun sqlType(): String = currentDialect.dataTypeProvider.textType()

  override fun valueFromDB(value: Any): Lang = when (value) {
    is Lang -> value
    is String -> Lang.valueOf(value)
    else -> error("Cannot convert $value to Lang")
  }

  override fun valueToDB(value: Lang?): Any = value?.toString() ?: error("Cannot convert null to Lang")
}
