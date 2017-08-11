package tech.gdragon.db.table

import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

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
    val volume = decimal("volume", 3, 2)
    val channels = reference("channels", Channels)
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

  val allTables = arrayOf(Guilds, Settings, Aliases, SettingsAliases, SettingsChannels)
}
