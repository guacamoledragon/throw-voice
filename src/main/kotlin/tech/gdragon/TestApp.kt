package tech.gdragon

import io.minio.MinioClient
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
import synapticloop.b2.B2ApiClient
import tech.gdragon.data.DataStore
import tech.gdragon.db.Shim
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
  Shim.initializeDatabase("settings.db")
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
  Shim.initializeDatabase("settings.db")
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

fun uploadRecording() {
  val bucketId: String = System.getenv("B2_BUCKET_ID") ?: ""
  val bucketName: String = System.getenv("B2_BUCKET_NAME") ?: ""
  val dataDirectory: String = System.getenv("DATA_DIR") ?: ""
  val accountId: String = System.getenv("B2_ACCOUNT_ID") ?: ""
  val accountKey: String = System.getenv("B2_APP_KEY") ?: ""

  val filename = "data/recordings/alone.pcm"
  val b2Client = B2ApiClient(accountId, accountKey)
//  val result = b2Client.uploadFile(bucketId, filename, File(filename))

//  println("result = $result")
  println("result = ${b2Client.downloadUrl}/file/$filename")
}

fun testAutoJoin() {
  Shim.initializeDatabase("./data/settings.db")
  transaction {
    val settings = Guild.findById(333055724198559745L)?.settings

    settings
      ?.channels
      ?.firstOrNull { it.id.value == 41992802040138956L }
      ?.let { println(it.id.value) }
  }
}

fun removeUnusedGuilds() {
  Shim.initializeDatabase("./data/settings.db")

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
//  uploadRecording()
//  testAutoJoin()
//  removeUnusedGuilds()
  minio()
}


fun minio() {
  val minioClient = MinioClient("http://localhost:9000", System.getenv("B2_ACCOUNT_ID"), System.getenv("B2_APP_KEY"))
  val file = File("./data/test-data/mp3-encoded/4ceeafa2-17ac-4d5f-9a7b-9903c6f11fa8.mp3")
  val dataStore = DataStore.createDataStore("dev-recordings")
  val result = dataStore.upload("/333055724198559745/4ceeafa2-17ac-4d5f-9a7b-9903c6f11fa9.mp3", file)
  println("result = $result")
  /*minioClient.listBuckets().forEach { bucket ->
    println("bucket = ${bucket.name()}")
  }

  val objectUrl = minioClient.getObjectUrl("dev-recordings", "/333055724198559745/0245a36c-654a-4b3b-8718-4e9d99f21fc3.mp3")
  println("recordingObject = $objectUrl")*/

//  minioClient.putObject("dev-recordings", "/333055724198559745/4ceeafa2-17ac-4d5f-9a7b-9903c6f11fa8.mp3", "./data/test-data/mp3-encoded/4ceeafa2-17ac-4d5f-9a7b-9903c6f11fa8.mp3")

  val statObject = minioClient.statObject("dev-recordings", "/333055724198559745/4ceeafa2-17ac-4d5f-9a7b-9903c6f11fa9.mp3")
  println("statObject = $statObject")
}
