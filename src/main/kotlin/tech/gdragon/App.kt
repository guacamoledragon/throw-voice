@file:JvmName("App")
package tech.gdragon

import mu.KotlinLogging
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import tech.gdragon.data.dataStore
import tech.gdragon.db.initializeDatabase
import tech.gdragon.discord.Bot
import tech.gdragon.discord.discordBot
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate

val logger = KotlinLogging.logger { }

fun main(args: Array<String>) {
  val app = startKoin {
    printLogger(Level.INFO)
    fileProperties("/defaults.properties")
    environmentProperties()
    modules(httpServer, discordBot, dataStore)
  }

  val dataDir = app.koin.getProperty("DATA_DIR", "./")
  initializeDataDirectory(dataDir)
  initializeDatabase("$dataDir/${app.koin.getProperty<String>("DB_NAME")}")

  logger.info("Starting background process to remove unused Guilds.")
  Timer("remove-old-guilds", true)
    .scheduleAtFixedRate(0L, Duration.ofDays(1L).toMillis()) {
      val jda = app.koin.get<Bot>().api
      val afterDays = app.koin.getProperty("BOT_LEAVE_GUILD_AFTER", 30)

      if(afterDays <= 0) {
        logger.info { "Disabling remove-old-guilds Timer." }
        this.cancel()
      } else {
        BotUtils.leaveAncientGuilds(jda, afterDays)
      }
    }

  HttpServer()
    .also {
      logger.info { "Starting HTTP Server: http://localhost:${it.port}" }
      it.server.start()
    }
}

/**
 * Creates the data directory and cleans up any remnant MP3 files in there
 */
private fun initializeDataDirectory(dataDirectory: String) {
  try {
    val recordingsDir = "$dataDirectory/recordings/"
    logger.info("Creating recordings directory: $recordingsDir")
    val dir = Files.createDirectories(Paths.get(recordingsDir))

    Files
      .list(dir)
      .filter { path -> Files.isRegularFile(path) && path.toString().toLowerCase().endsWith(".mp3") }
      .forEach { path ->
        try {
          Files.delete(path)
          logger.info("Deleting file $path...")
        } catch (e: IOException) {
          logger.error("Could not delete: $path", e)
        }
      }
  } catch (e: IOException) {
    logger.error("Could not create recordings directory", e)
  }
}
