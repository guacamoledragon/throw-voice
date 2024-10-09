package tech.gdragon.db.dao

import net.dv8tion.jda.api.entities.User
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.exposedLogger
import tech.gdragon.db.table.Tables.Aliases
import tech.gdragon.db.table.Tables.Applications
import tech.gdragon.db.table.Tables.Channels
import tech.gdragon.db.table.Tables.Guilds
import tech.gdragon.db.table.Tables.Recordings
import java.time.Duration
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
  var voiceChannel by Channels.voiceChannel
}

class Guild(id: EntityID<Long>) : LongEntity(id) {
  companion object : LongEntityClass<Guild>(Guilds) {

    /**
     * Creates a Guild if not present and returns it.
     *
     * A Guild depends on a Settings record, so that's also checked. We must have one associated with the Guild.
     */
    @JvmStatic
    fun findOrCreate(id: Long, name: String): Guild {
      val guild = Guild.findById(id) ?: Guild.new(id) {
        exposedLogger.info("Creating Guild database entry for: $name")
        this.name = name
      }

      Settings.find { SettingsTable.guild eq guild.id }.firstOrNull() ?: Settings.new {
        exposedLogger.info("Creating Settings database entry for guild: ${guild.name}")
        this.guild = guild
      }

      return guild
    }
  }

  var active by Guilds.active
  val joinedOn by Guilds.joinedOn
  var unjoinedOn by Guilds.unjoinedOn
  var name by Guilds.name
  var lastActiveOn by Guilds.lastActiveOn
  val settings by Settings backReferencedOn SettingsTable.guild
}

class Recording(id: EntityID<String>) : Entity<String>(id) {
  companion object : EntityClass<String, Recording>(Recordings) {
    fun findIdLike(pattern: String, guildId: Long?, limit: Int) = run {
      val withGuild = if (guildId != null) Recordings.guild.eq(guildId) else Op.TRUE
      find { withGuild.and(Recordings.id like pattern) }
        .orderBy(Recordings.createdOn to SortOrder.DESC)
        .limit(limit)
    }
  }

  val createdOn by Recordings.createdOn

  var channel by Channel referencedOn Recordings.channel
  var size by Recordings.size
  var speakers: MutableSet<User> = mutableSetOf()
  var modifiedOn by Recordings.modifiedOn
  var duration: Duration = Duration.ZERO
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
