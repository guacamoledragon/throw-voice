package tech.gdragon.db.table

import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object Tables {
  object Guilds : LongIdTable() {
    //  override val id = long("id").primaryKey().entityId()
    val name = varchar("name", 128)
    val settings = reference("settings", Settings)
  }

  object Settings : IntIdTable() {
    val autoSave = bool("autoSave")
  }

  object Aliases : IntIdTable() {
    val name = varchar("name", 128)
    val alias = varchar("alias", 128)
  }

  object SettingsAliases : Table() {
    val settings = reference("settings", Settings, ReferenceOption.CASCADE)
    val alias = reference("alias", Aliases, ReferenceOption.CASCADE)

    init {
      index(true, settings, alias)
    }
  }

  val allTables = arrayOf(Guilds, Settings, Aliases, SettingsAliases)
}
