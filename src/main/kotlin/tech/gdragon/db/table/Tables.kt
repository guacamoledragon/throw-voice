package tech.gdragon.db.table

import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import java.math.BigDecimal

object Tables {
  object Guilds : LongIdTable() {
    //  override val id = long("id").primaryKey().entityId()
    val name = text("name")
    val settings = reference("settings", Settings)
  }

  object Settings : IntIdTable() {
    val autoSave = bool("autoSave")
    val prefix = text("prefix").default("!")
    val defaultTextChannel = reference("defaultTextChannel", Channels)
    val volume = decimal("volume", 3, 2).default(BigDecimal.valueOf(0.8))
    val channels = reference("channels", Channels)
    val alertsBlackList = reference("alertsBlackList", Users)
  }

  object Aliases : IntIdTable() {
    val name = text("name")
    val alias = text("alias")
  }

  object Channels : IntIdTable() {
    val name = text("name")
    val autojoin = integer("autojoin").nullable()
    val autoleave = integer("autojoin").nullable()
  }

  object Users : IntIdTable() {
    val name = text("name")
  }

  object SettingsAliases : Table() {
    val settings = reference("settings", Settings, ReferenceOption.CASCADE)
    val alias = reference("alias", Aliases, ReferenceOption.CASCADE)

    init {
      index(true, settings, alias)
    }
  }

  object SettingsChannels : Table() {
    val settings = reference("settings", Settings, ReferenceOption.CASCADE)
    val channel = reference("channel", Channels, ReferenceOption.CASCADE)

    init {
      index(true, settings, channel)
    }
  }

  object SettingsUsers : Table() {
    val settings = reference("settings", Settings, ReferenceOption.CASCADE)
    val user = reference("user", Users, ReferenceOption.CASCADE)

    init {
      index(true, settings, user)
    }
  }

  val allTables = arrayOf(Aliases, Channels, Guilds, Settings, Users, SettingsAliases, SettingsChannels, SettingsUsers)
}
