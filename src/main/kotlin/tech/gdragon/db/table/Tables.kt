package tech.gdragon.db.table

import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import java.math.BigDecimal

object Tables {
  object Guilds : LongIdTable() {
    val name = text("name")
    val settings = reference("settings", Settings)
  }

  object Settings : IntIdTable() {
    val autoSave = bool("autoSave").default(false)
    val prefix = text("prefix").default("!")
    val defaultTextChannel = reference("defaultTextChannel", Channels).nullable()
    val volume = decimal("volume", 3, 2).default(BigDecimal.valueOf(0.8))
  }

  object Aliases : IntIdTable() {
    val name = text("name")
    val alias = text("alias")
    val settings = reference("settings", Settings)
  }

  object Channels : LongIdTable() {
    val name = text("name")
    val autoJoin = integer("autoJoin").nullable()
    val autoLeave = integer("autoLeave").default(1)
  }

  object Users : LongIdTable() {
    val name = text("name")
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

  val allTables = arrayOf(Aliases, Channels, Guilds, Settings, Users, SettingsChannels, SettingsUsers)
}
