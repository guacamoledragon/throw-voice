package tech.gdragon.db.table

import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.joda.time.DateTime
import tech.gdragon.db.DateColumnType
import tech.gdragon.db.LanguageColumnType
import tech.gdragon.db.nowUTC
import tech.gdragon.i18n.Lang
import java.math.BigDecimal

object Tables {
  object Guilds : LongIdTable() {
    val active = bool("active").default(true)
    val name = text("name")
    val region = text("region")
    val createdOn = registerColumn<DateTime>("created_on", DateColumnType(true)).clientDefault(::nowUTC)
    val lastActiveOn = registerColumn<DateTime>("last_active_on", DateColumnType(true)).clientDefault(::nowUTC)
  }

  object Settings : LongIdTable() {
    val autoSave = bool("autosave").default(false)
    val prefix = text("prefix").default("!")
    val defaultTextChannel = long("defaulttextchannel").nullable()
    val volume = decimal("volume", 3, 2).default(BigDecimal.valueOf(0.8))
    val guild = reference("guild", Guilds, ReferenceOption.CASCADE).uniqueIndex()
    val language = registerColumn<Lang>("language", LanguageColumnType()).default(Lang.EN)
  }

  object Aliases : IntIdTable() {
    val name = text("name")
    val alias = text("alias")
    val settings = reference("settings", Settings, ReferenceOption.CASCADE)
  }

  object Channels : LongIdTable() {
    val name = text("name")
    val autoRecord = integer("autorecord").nullable()
    val autoStop = integer("autostop").nullable()
    val settings = reference("settings", Settings, ReferenceOption.CASCADE)
  }

  object Recordings : LongIdTable() {
    val channel = reference("channel", Channels)
    val size = long("size").default(0)
    val createdOn = registerColumn<DateTime>("created_on", DateColumnType(true)).clientDefault(::nowUTC)
    val modifiedOn = registerColumn<DateTime>("modified_on", DateColumnType(true)).nullable()
    val url = text("url").nullable()
    val guild = reference("guild", Guilds, ReferenceOption.CASCADE)
  }

  val allTables = arrayOf(Aliases, Channels, Guilds, Recordings, Settings)
}
