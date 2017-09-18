package tech.gdragon.db.table

import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.dao.LongIdTable
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

  object Channels : IntIdTable() {
    val name = text("name")
    val discordId = long("discordId")
    val autoJoin = integer("autoJoin").nullable()
    val autoLeave = integer("autoLeave").default(1)
    val settings = reference("settings", Settings)
  }

  object Users : IntIdTable() {
    val name = text("name")
    val discordId = long("discordId").uniqueIndex()
    val settings = reference("settings", Settings).uniqueIndex()

    init {
        uniqueIndex(discordId, settings)
    }
  }

  val allTables = arrayOf(Aliases, Channels, Guilds, Settings, Users)
}
