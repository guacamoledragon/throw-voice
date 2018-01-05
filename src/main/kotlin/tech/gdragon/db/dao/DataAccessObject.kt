package tech.gdragon.db.dao

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.db.table.Tables.Aliases
import tech.gdragon.db.table.Tables.Channels
import tech.gdragon.db.table.Tables.Guilds
import tech.gdragon.db.table.Tables.Users
import tech.gdragon.db.table.Tables.Settings as SettingsTable

class Alias(id: EntityID<Int>) : IntEntity(id) {
  companion object : IntEntityClass<Alias>(Aliases) {
    private val aliases = listOf("info" to "help", "record" to "join", "stop" to "leave", "symbol" to "prefix")

    fun createDefaultAliases(settings: Settings) = aliases.map { (alias, name) ->
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

class Channel(id: EntityID<Long>) : LongEntity(id) {
  companion object : LongEntityClass<Channel>(Channels) {
    fun findOrCreate(id: Long, name: String, guildId: Long, guildName: String): Iterable<Channel> = transaction {
      val guild = Guild.findOrCreate(guildId, guildName)
      val channels = Channel.find { (Channels.settings eq guild.settings.id) and (Channels.id eq id) }

      if (channels.empty()) {
        val newChannel = Channel.new(id) {
          this.name = name
          this.settings = guild.settings
        }

        commit()

        listOf(newChannel)
      } else {
        channels
      }
    }
  }

  var name by Channels.name
  var autoJoin by Channels.autoJoin
  var autoLeave by Channels.autoLeave
  var settings by Settings referencedOn Channels.settings
}

class Guild(id: EntityID<Long>) : LongEntity(id) {
  companion object : LongEntityClass<Guild>(Guilds) {

    @JvmStatic
    fun findOrCreate(id: Long, name: String): Guild = transaction {
      val guild = Guild.findById(id)

      return@transaction if (guild != null) {
        guild
      } else {
        val settings = Settings.new {
          this.guild = Guild.new(id) {
            this.name = name
          }
        }

        commit()

        Alias.createDefaultAliases(settings)
        exposedLogger.info("Creating database entry for: ${settings.guild.name}")
        settings.guild
      }
    }
  }

  var name by Guilds.name
  val settings by Settings backReferencedOn SettingsTable.guild
}

class Settings(id: EntityID<Long>) : LongEntity(id) {
  companion object : LongEntityClass<Settings>(SettingsTable)

  var autoSave by SettingsTable.autoSave
  var defaultTextChannel by SettingsTable.defaultTextChannel
  var prefix by SettingsTable.prefix
  var volume by SettingsTable.volume
  var guild by Guild referencedOn SettingsTable.guild

  val alertBlacklist by User referrersOn Users.settings
  val channels by Channel referrersOn Channels.settings
  val aliases by Alias referrersOn Aliases.settings
}

class User(id: EntityID<Long>) : LongEntity(id) {
  companion object : LongEntityClass<User>(Users) {
    @JvmStatic
    fun findOrCreate(id: Long, name: String, settings: Settings): User = transaction {
      val users = User.find {
        (Users.id eq id) and (Users.settings eq settings.id)
      }

      if (users.empty()) {
        User.new(id) {
          this.name = name
          this.settings = settings
        }
      } else {
        users.first()
      }
    }
  }

  var name by Users.name
  var settings by Settings referencedOn Users.settings
}
