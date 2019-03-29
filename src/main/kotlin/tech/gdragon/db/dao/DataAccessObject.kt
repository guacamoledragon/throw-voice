package tech.gdragon.db.dao

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.db.nowUTC
import tech.gdragon.db.table.Tables.Aliases
import tech.gdragon.db.table.Tables.Channels
import tech.gdragon.db.table.Tables.Guilds
import tech.gdragon.db.table.Tables.Recordings
import tech.gdragon.db.table.Tables.Users
import tech.gdragon.db.table.Tables.Settings as SettingsTable

class Alias(id: EntityID<Int>) : IntEntity(id) {
  companion object : IntEntityClass<Alias>(Aliases)

  var name by Aliases.name
  var alias by Aliases.alias
  var settings by Settings referencedOn Aliases.settings
}

class Channel(id: EntityID<Long>) : LongEntity(id) {
  companion object : LongEntityClass<Channel>(Channels) {
    fun findOrCreate(id: Long, name: String, guild: Guild): Channel {

      return find { (Channels.settings eq guild.settings.id) and (Channels.id eq id) }.firstOrNull() ?: Channel.new(id) {
        this.name = name
        this.settings = guild.settings
      }
    }
  }

  var name by Channels.name
  var autoRecord by Channels.autoRecord
  @Deprecated("This feature is broken", level = DeprecationLevel.ERROR)
  var autoLeave by Channels.autoLeave
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

    // TODO: Remove region from the method signature
    fun updateActivity(guildId: Long, region: String) {
      transaction {
        Guild.findById(guildId)?.let {
          it.lastActiveOn = nowUTC()
          it.region = region
        }
      }
    }
  }

  val createdOn by Guilds.createdOn
  var name by Guilds.name
  var lastActiveOn by Guilds.lastActiveOn
  var region by Guilds.region
  val settings by Settings backReferencedOn SettingsTable.guild
}

class Recording(id: EntityID<Long>) : LongEntity(id) {
  companion object : LongEntityClass<Recording>(Recordings)

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
  var guild by Guild referencedOn SettingsTable.guild

  val alertBlacklist by User referrersOn Users.settings
  val channels by Channel referrersOn Channels.settings
  val aliases by Alias referrersOn Aliases.settings
}

class User(id: EntityID<Long>) : LongEntity(id) {
  companion object : LongEntityClass<User>(Users) {
    @JvmStatic
    fun findOrCreate(id: String, settings: Settings): User = transaction {
      val users = User.find {
        (Users.name eq id) and (Users.settings eq settings.id)
      }

      if (users.empty()) {
        User.new {
          this.name = id
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
