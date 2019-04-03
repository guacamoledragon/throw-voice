package tech.gdragon.db.table

import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.joda.time.DateTime
import tech.gdragon.db.DateColumnType
import tech.gdragon.db.nowUTC
import java.math.BigDecimal

object Tables {
  object Guilds : LongIdTable() {
    val name = text("name")
    val region = text("region")
    val createdOn = registerColumn<DateTime>("created_on", DateColumnType(true)).clientDefault(::nowUTC)
    val lastActiveOn = registerColumn<DateTime>("last_active_on", DateColumnType(true)).clientDefault(::nowUTC)
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
    val autoRecord = integer("autoRecord").nullable()
    val autoStop = integer("autoStop").nullable()
    val settings = reference("settings", Settings, ReferenceOption.CASCADE)
  }

  object Users : LongIdTable() {
    val name = text("name")
    val settings = reference("settings", Settings, ReferenceOption.CASCADE)

    init {
      index(true, name, settings)
    }
  }

  object Recordings : LongIdTable() {
    val channel = reference("channel", Channels)
    val size = long("size").default(0)
    val createdOn = registerColumn<DateTime>("created_on", DateColumnType(true)).clientDefault(::nowUTC)
    val modifiedOn = registerColumn<DateTime>("modified_on", DateColumnType(true)).nullable()
    val url = text("url").nullable()
    val guild = reference("guild", Guilds, ReferenceOption.CASCADE)
  }

  val allTables = arrayOf(Aliases, Channels, Guilds, Recordings, Settings, Users)
}
