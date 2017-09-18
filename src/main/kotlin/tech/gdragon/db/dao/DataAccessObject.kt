package tech.gdragon.db.dao

import org.jetbrains.exposed.dao.*
import tech.gdragon.db.table.Tables.Aliases
import tech.gdragon.db.table.Tables.Channels
import tech.gdragon.db.table.Tables.Guilds
import tech.gdragon.db.table.Tables.Users
import tech.gdragon.db.table.Tables.Settings as SettingsTable

class Alias(id: EntityID<Int>) : IntEntity(id) {
  companion object : IntEntityClass<Alias>(Aliases) {
    private val aliases = listOf("info" to "help", "record" to "join", "stop" to "leave", "symbol" to "prefix")

    fun createDefaultAliases(settings: Settings) = aliases.map { (name, alias) ->
      Alias.new {
        this.name = name
        this.alias = alias
        this.settings = settings
      }
    }
  }

  var name by Aliases.name
  var alias by Aliases.alias
  var settings by Settings referencedOn Aliases.settings
}

class Channel(id: EntityID<Int>) : IntEntity(id) {
  companion object : IntEntityClass<Channel>(Channels)

  var name by Channels.name
  var discordId by Channels.discordId
  var autoJoin by Channels.autoJoin
  var autoLeave by Channels.autoLeave
  var settings by Settings referencedOn Channels.settings
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

  val alertBlacklist by User referrersOn Users.settings
  val channels by Channel referrersOn Channels.settings
  val aliases by Alias referrersOn Aliases.settings
}

class User(id: EntityID<Int>) : IntEntity(id) {
  companion object : IntEntityClass<User>(Users) {
    @JvmStatic fun create(id: Long, name: String, settings: Settings): User {
      return User.new {
        this.discordId = id
        this.name = name
        this.settings = settings
      }
    }
  }

  var name by Users.name
  var discordId by Users.discordId
  var settings by Settings referencedOn Users.settings
}
