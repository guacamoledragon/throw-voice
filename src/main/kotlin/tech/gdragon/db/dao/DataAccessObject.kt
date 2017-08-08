package tech.gdragon.db.dao

import org.jetbrains.exposed.dao.*
import tech.gdragon.db.table.Guilds
import tech.gdragon.db.table.Settings as SettingsTable

class Guild(id: EntityID<Long>) : LongEntity(id) {
  companion object : LongEntityClass<Guild>(Guilds)

  var name by Guilds.name
  var settings by Settings referencedOn Guilds.settings
}

class Settings(id: EntityID<Int>) : IntEntity(id) {
  companion object : IntEntityClass<Settings>(SettingsTable)

  var autoSave by SettingsTable.autoSave
}
