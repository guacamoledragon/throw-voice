package tech.gdragon

import net.dv8tion.jda.core.AccountType
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceJoinEvent
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceLeaveEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.db.Shim
import tech.gdragon.db.dao.Alias
import tech.gdragon.db.dao.Channel
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.dao.Settings
import tech.gdragon.db.table.Tables
import java.sql.Connection

fun basicTest() {
  val database = "settings.db"
  Database.connect("jdbc:sqlite:$database", driver = "org.sqlite.JDBC")
  TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_READ_UNCOMMITTED

  val aliases: List<Alias> = transaction {
    SchemaUtils.drop(*Tables.allTables)
    SchemaUtils.create(*Tables.allTables)

//    val guild = Guild[333055724198559745L]

    val guild = Guild.new(333055724198559745L) {
      name = "Guacamole Dragon"
      settings = Settings.new {}
    }

    Alias.createDefaultAliases(guild.settings)

    val channel = Channel.new {
      name = "bot-testing"
      discordId = 346340766039146506L
      settings = guild.settings
    }

    println(guild.settings.autoSave)
    guild.settings.aliases.forEach { println("${it.name} -> ${it.alias}") }

    return@transaction guild.settings.aliases.toList()
  }

  aliases.forEach { println("${it.name} -> ${it.alias}") }
}

fun testBiggestChannel() {
  Shim.initializeDatabase("settings.db")
  JDABuilder(AccountType.BOT)
    .setToken(System.getenv("TOKEN"))
    .addEventListener(object: ListenerAdapter() {
      override fun onGuildVoiceJoin(event: GuildVoiceJoinEvent) {
        println("event.guild = ${event.guild}")
        println("Joined ${event.channelJoined}")
        println("Largest channel: ${BotUtils.biggestChannel(event.guild)}")
        super.onGuildVoiceJoin(event)
      }

      override fun onGuildVoiceLeave(event: GuildVoiceLeaveEvent) {
        println("Leaving ${event.channelLeft}")
        super.onGuildVoiceLeave(event)
      }
    })
    .buildBlocking()
}

fun testAlerts() {
  Shim.initializeDatabase("settings.db")
  JDABuilder(AccountType.BOT)
    .setToken(System.getenv("TOKEN"))
    .addEventListener(object: ListenerAdapter() {
      override fun onGuildVoiceJoin(event: GuildVoiceJoinEvent?) {
        println("Sending alerts to users that joined ${event?.channelJoined}.")
        BotUtils.alert(event?.channelJoined)
        super.onGuildVoiceJoin(event)
      }
    })
    .buildBlocking()
}

fun main(args: Array<String>) {
//  testAlerts()
  basicTest()
}
