package tech.gdragon.db.dao

import org.jetbrains.exposed.dao.*
import tech.gdragon.db.table.Tables.Aliases
import tech.gdragon.db.table.Tables.Channels
import tech.gdragon.db.table.Tables.Guilds
import tech.gdragon.db.table.Tables.Users
import tech.gdragon.db.table.Tables.SettingsAliases
import tech.gdragon.db.table.Tables.Settings as SettingsTable

class Alias(id: EntityID<Int>) : IntEntity(id) {
  companion object : IntEntityClass<Alias>(Aliases)

  var name by Aliases.name
  var alias by Aliases.alias
}

class Channel(id: EntityID<Int>) : IntEntity(id) {
  companion object : IntEntityClass<Channel>(Channels)

  var name by Channels.name
  var autojoin by Channels.autojoin
  var autoleave by Channels.autoleave
}

class Guild(id: EntityID<Long>) : LongEntity(id) {
  companion object : LongEntityClass<Guild>(Guilds)

  var name by Guilds.name
  var settings by Settings referencedOn Guilds.settings
}

class Settings(id: EntityID<Int>) : IntEntity(id) {
  companion object : IntEntityClass<Settings>(SettingsTable)

  var autoSave by SettingsTable.autoSave
  var defaultTextChannel by SettingsTable.defaultTextChannel
  var prefix by SettingsTable.prefix
  var volume by SettingsTable.volume

  var alertBlacklist by User via Users
  var channels by Channel via Channelsa,
  var aliases by Alias via SettingsAliases
}

class User(id: EntityID<Int>) : IntEntity(id) {
  companion object : IntEntityClass<User>(Users)

  var name by Users.name
}
