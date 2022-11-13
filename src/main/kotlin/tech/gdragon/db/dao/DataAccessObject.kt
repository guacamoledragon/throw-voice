package tech.gdragon.db.dao

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager
import tech.gdragon.db.table.Tables.Aliases
import tech.gdragon.db.table.Tables.Applications
import tech.gdragon.db.table.Tables.Channels
import tech.gdragon.db.table.Tables.Guilds
import tech.gdragon.db.table.Tables.Recordings
import tech.gdragon.db.table.Tables.Settings as SettingsTable

class Alias(id: EntityID<Int>) : IntEntity(id) {
  companion object : IntEntityClass<Alias>(Aliases) {
    fun findOrCreate(name: String, alias: String, settings: Settings): Alias {
      return find { (Aliases.name eq name) and (Aliases.alias eq alias) and (Aliases.settings eq settings.id) }
        .firstOrNull()
        ?: Alias.new {
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

class Application(id: EntityID<Long>) : LongEntity(id) {
  companion object : LongEntityClass<Application>(Applications) {
    fun findOrCreate(id: Long) = Application.findById(id) ?: Application.new(id) {}
  }

  val createdOn by Applications.createdOn
}

class Channel(id: EntityID<Long>) : LongEntity(id) {
  companion object : LongEntityClass<Channel>(Channels) {
    @Deprecated("Pass in Guild ID instead of Guild instance.", level = DeprecationLevel.WARNING)
    fun findOrCreate(id: Long, name: String, guild: Guild): Channel {

      return find { (Channels.settings eq guild.settings.id) and (Channels.id eq id) }.firstOrNull()
        ?: Channel.new(id) {
          this.name = name
          this.settings = guild.settings
        }
    }

    fun findOrCreate(id: Long, name: String, guildId: Long): Channel {
      val guild = Guild[guildId]

      return find { (Channels.settings eq guild.settings.id) and (Channels.id eq id) }.firstOrNull()
        ?: Channel.new(id) {
          this.name = name
          this.settings = guild.settings
        }
    }
  }

  var name by Channels.name
  var autoRecord by Channels.autoRecord
  var autoStop by Channels.autoStop
  var settings by Settings referencedOn Channels.settings
}

class Guild(id: EntityID<Long>) : LongEntity(id) {
  companion object : LongEntityClass<Guild>(Guilds) {

    @JvmStatic
    fun findOrCreate(id: Long, name: String, region: String): Guild {
      return Guild.findById(id) ?: Guild.new(id) {
        this.name = name
        this.region = region
      }.also { guild ->
        // Please ensure Guild is created before proceeding
        exposedLogger.info("Creating Guild database entry for: ${guild.name}")
        TransactionManager.current().commit()

        Settings.new { this.guild = guild }
      }
    }
  }

  var active by Guilds.active
  val createdOn by Guilds.createdOn
  var name by Guilds.name
  var lastActiveOn by Guilds.lastActiveOn
  var region by Guilds.region
  val settings by Settings backReferencedOn SettingsTable.guild
}

class Recording(id: EntityID<String>) : Entity<String>(id) {
  companion object : EntityClass<String, Recording>(Recordings)

  val createdOn by Recordings.createdOn

  var channel by Channel referencedOn Recordings.channel
  var size by Recordings.size
  var modifiedOn by Recordings.modifiedOn
  var url by Recordings.url
  var guild by Guild referencedOn Recordings.guild
}

class Settings(id: EntityID<Long>) : LongEntity(id) {
  companion object : LongEntityClass<Settings>(SettingsTable)

  var autoSave by SettingsTable.autoSave
  var defaultTextChannel by SettingsTable.defaultTextChannel
  var prefix by SettingsTable.prefix
  var volume by SettingsTable.volume
  var language by SettingsTable.language
  var guild by Guild referencedOn SettingsTable.guild

  val channels by Channel referrersOn Channels.settings
  val aliases by Alias referrersOn Aliases.settings
}
