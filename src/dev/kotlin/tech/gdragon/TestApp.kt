package tech.gdragon

import de.sciss.jump3r.lowlevel.LameEncoder
import net.dv8tion.jda.api.audio.AudioReceiveHandler
import org.apache.commons.io.FileUtils
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.between
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.not
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.environmentProperties
import org.koin.fileProperties
import tech.gdragon.db.dao.Alias
import tech.gdragon.db.dao.Channel
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.table.Tables
import tech.gdragon.db.table.Tables.Guilds
import java.io.File
import java.sql.Connection

fun dropAllTables() {
  val database = "settings.db"
  Database.connect("jdbc:sqlite:$database", driver = "org.sqlite.JDBC")
  TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_READ_UNCOMMITTED

  transaction {
    SchemaUtils.drop(*Tables.allTables)
  }
}

fun basicTest() {
  val database = "settings.db"
  Database.connect("jdbc:sqlite:$database", driver = "org.sqlite.JDBC", setupConnection = {
    val statement = it.createStatement()
    statement.executeUpdate("PRAGMA foreign_keys = ON")
  })
  TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_READ_UNCOMMITTED

  transaction {
    SchemaUtils.drop(*Tables.allTables)
    SchemaUtils.create(*Tables.allTables)
  }

  val guild = Guild.findOrCreate(333055724198559745L, "Guacamole Dragon")

  transaction {
    Channel.new(346340766039146506L) {
      name = "bot-testing"
      settings = guild.settings
    }
  }

  val aliases: List<Alias> = transaction { guild.settings.aliases.toList() }

  aliases.forEach { println("${it.name} -> ${it.alias}") }
}

fun testAutoJoin() {
//  initializeDatabase("./data/settings.db")
  transaction {
    val settings = Guild.findById(333055724198559745L)?.settings

    settings
      ?.channels
      ?.firstOrNull { it.id.value == 41992802040138956L }
      ?.let { println(it.id.value) }
  }
}

fun removeUnusedGuilds() {
  transaction {
    Guilds.deleteWhere {
      val now = DateTime.now()
      not(lastActiveOn.between(now.minusDays(30), now))
    }
  }
}

fun pcm2mp3(pcm: String) {
  val pcmBytes = FileUtils.readFileToByteArray(File(pcm))
  val encoder = LameEncoder(AudioReceiveHandler.OUTPUT_FORMAT, 128, LameEncoder.CHANNEL_MODE_AUTO, LameEncoder.QUALITY_HIGHEST, true)
  val buffer = ByteArray(pcmBytes.size)
  val encodedCount = encoder.encodeBuffer(pcmBytes, 0, pcmBytes.size, buffer)

  FileUtils.writeByteArrayToFile(File("$pcm.mp3"), buffer)
}

fun initializeKoin() = startKoin {
  printLogger(Level.INFO)
  fileProperties("/defaults.properties")
  fileProperties("/overrides.properties")
  environmentProperties()
}

fun main() {
//  testAlerts()
//  basicTest()
//  dropAllTables()
//  testAutoJoin()
//  removeUnusedGuilds()
}

