package tech.gdragon.db.table

import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.dao.LongIdTable

object Guilds : LongIdTable() {
//  override val id = long("id").primaryKey().entityId()
  val name = varchar("name", 128)
  val settings = reference("settings", Settings)
}

object Settings : IntIdTable() {
  val autoSave = bool("autoSave")
}
