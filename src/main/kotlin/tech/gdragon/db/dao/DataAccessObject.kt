package tech.gdragon.db.dao

import org.jetbrains.exposed.dao.*
import tech.gdragon.db.table.Tables.Aliases
import tech.gdragon.db.table.Tables.Guilds
import tech.gdragon.db.table.Tables.SettingsAliases
import tech.gdragon.db.table.Tables.Settings as SettingsTable


class Guild(id: EntityID<Long>) : LongEntity(id) {
  companion object : LongEntityClass<Guild>(Guilds)

  var name by Guilds.name
  var settings by Settings referencedOn Guilds.settings
}

class Settings(id: EntityID<Int>) : IntEntity(id) {
  companion object : IntEntityClass<Settings>(SettingsTable)

  var autoSave by SettingsTable.autoSave
  var aliases by Alias via SettingsAliases
}

class Alias(id: EntityID<Int>) : IntEntity(id) {
  companion object : IntEntityClass<Alias>(Aliases)

  var name by Aliases.name
  var alias by Aliases.alias
}
