package tech.gdragon.db.table

import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import java.math.BigDecimal

object Tables {
  object Guilds : LongIdTable() {
    val name = text("name")
  }

  object Settings : LongIdTable() {
    val autoSave = bool("autoSave").default(false)
    val prefix = text("prefix").default("!")
    val defaultTextChannel = long("defaultTextChannel").nullable()
    val volume = decimal("volume", 3, 2).default(BigDecimal.valueOf(0.8))
    val guild = reference("guild", Guilds, ReferenceOption.CASCADE).uniqueIndex()
  }

  object Aliases : IntIdTable() {
    val name = text("name")
    val alias = text("alias")
    val settings = reference("settings", Settings, ReferenceOption.CASCADE)
  }

  object Channels : LongIdTable() {
    val name = text("name")
    val autoJoin = integer("autoJoin").nullable()
    val autoLeave = integer("autoLeave").default(1)
    val settings = reference("settings", Settings, ReferenceOption.CASCADE)
  }

  object Users : LongIdTable() {
    val name = text("name")
    val settings = reference("settings", Settings, ReferenceOption.CASCADE).uniqueIndex()
  }

  val allTables = arrayOf(Aliases, Channels, Guilds, Settings, Users)
}
