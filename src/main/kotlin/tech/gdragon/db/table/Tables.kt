package tech.gdragon.db.table

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestamp
import tech.gdragon.db.LanguageColumnType
import tech.gdragon.db.now
import tech.gdragon.i18n.Lang
import java.math.BigDecimal

object Tables {
  object Guilds : LongIdTable() {
    val active = bool("active").default(true)
    val name = text("name")
    val region = text("region").default("UNKNOWN")
    val joinedOn = timestamp("joined_on").clientDefault(::now)
    val unjoinedOn = timestamp("unjoined_on").nullable()
    val lastActiveOn = timestamp("last_active_on").clientDefault(::now)
  }

  object Settings : LongIdTable() {
    val autoSave = bool("autosave").default(false)
    val prefix = text("prefix").default("!")
    val defaultTextChannel = long("defaulttextchannel").nullable()
    val volume = decimal("volume", 3, 2).default(BigDecimal.valueOf(0.8))
    val guild = reference("guild", Guilds, ReferenceOption.CASCADE).uniqueIndex()
    val language = registerColumn("language", LanguageColumnType()).default(Lang.EN)
  }

  object Aliases : IntIdTable() {
    val name = text("name")
    val alias = text("alias")
    val settings = reference("settings", Settings, ReferenceOption.CASCADE)
  }

  object Applications : LongIdTable() {
    val createdOn = timestamp("created_on").clientDefault(::now)
  }

  object Channels : LongIdTable() {
    val name = text("name")
    val autoRecord = integer("autorecord").nullable()
    val autoStop = integer("autostop").nullable()
    val voiceChannel = long("voicechannel").nullable()
    val settings = reference("settings", Settings, ReferenceOption.CASCADE)
  }

  object Recordings : IdTable<String>() {
    override val id: Column<EntityID<String>> = text("id").entityId()
    override val primaryKey: PrimaryKey = PrimaryKey(id)

    val channel = reference("channel", Channels)
    val size = long("size").default(0)
    val createdOn = timestamp("created_on").clientDefault(::now)
    val modifiedOn = timestamp("modified_on").nullable()
    val url = text("url").nullable()
    val guild = reference("guild", Guilds, ReferenceOption.CASCADE)
  }

  val allTables = arrayOf(Applications, Aliases, Channels, Guilds, Recordings, Settings)
}
