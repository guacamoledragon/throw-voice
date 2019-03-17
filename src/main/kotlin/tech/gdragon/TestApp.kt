package tech.gdragon

import net.dv8tion.jda.core.AccountType
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceJoinEvent
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceLeaveEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.not
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import tech.gdragon.db.dao.Alias
import tech.gdragon.db.dao.Channel
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.initializeDatabase
import tech.gdragon.db.table.Tables
import tech.gdragon.db.table.Tables.Guilds
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

  val guild = Guild.findOrCreate(333055724198559745L, "Guacamole Dragon", "US West")

  transaction {
    Channel.new(346340766039146506L) {
      name = "bot-testing"
      settings = guild.settings
    }
  }

  val aliases: List<Alias> = transaction { guild.settings.aliases.toList() }

  aliases.forEach { println("${it.name} -> ${it.alias}") }
}

fun testBiggestChannel() {
  initializeDatabase("settings.db")
  JDABuilder(AccountType.BOT)
    .setToken(System.getenv("TOKEN"))
    .addEventListener(object : ListenerAdapter() {
      override fun onGuildVoiceJoin(event: GuildVoiceJoinEvent) {
        println("event.guild = ${event.guild}")
        println("Joined ${event.channelJoined}")
//        println("Largest channel: ${BotUtils.biggestChannel(event.guild)}")
        super.onGuildVoiceJoin(event)
      }

      override fun onGuildVoiceLeave(event: GuildVoiceLeaveEvent) {
        println("Leaving ${event.channelLeft}")
        super.onGuildVoiceLeave(event)
      }
    })
    .build()
    .awaitReady()
}

fun testAlerts() {
  initializeDatabase("settings.db")
  JDABuilder(AccountType.BOT)
    .setToken(System.getenv("TOKEN"))
    .addEventListener(object : ListenerAdapter() {
      override fun onGuildVoiceJoin(event: GuildVoiceJoinEvent) {
        println("Sending alerts to users that joined ${event.channelJoined}.")
        BotUtils.sendMessage(null, "")
        super.onGuildVoiceJoin(event)
      }
    })
    .build()
    .awaitReady()
}

fun testAutoJoin() {
  initializeDatabase("./data/settings.db")
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
      not(Guilds.lastActiveOn.between(now.minusDays(30), now))
    }
  }
}


fun main(args: Array<String>) {
//  testAlerts()
//  basicTest()
//  dropAllTables()
//  testAutoJoin()
//  removeUnusedGuilds()
}

