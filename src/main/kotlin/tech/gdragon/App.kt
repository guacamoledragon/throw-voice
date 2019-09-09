@file:JvmName("App")

package tech.gdragon

import mu.KotlinLogging
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module
import tech.gdragon.data.DataStore
import tech.gdragon.db.initializeDatabase
import tech.gdragon.discord.Bot
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate

val logger = KotlinLogging.logger { }

fun main() {
  val app = startKoin {
    printLogger(Level.INFO)
    fileProperties("/defaults.properties")
    System.getenv("ENV")
      ?.let {
        if (it == "dev") {
          fileProperties("/dev.properties")
        }
      }
    environmentProperties()
    modules(
      module {
        single { Bot() }
        single { DataStore() }
      }
    )
  }

  val dataDir = app.koin.getProperty("DATA_DIR", "./")
  initializeDataDirectory(dataDir)
  initializeDatabase("$dataDir/${app.koin.getProperty<String>("DB_NAME")}")

  val bot =
    Bot().also {
      logger.info("Starting background process to remove unused Guilds.")
      Timer("remove-old-guilds", true)
        .scheduleAtFixedRate(0L, Duration.ofDays(1L).toMillis()) {
          val jda = it.api
          val afterDays = app.koin.getProperty("BOT_LEAVE_GUILD_AFTER", 30)

          if (afterDays <= 0) {
            logger.info { "Disabling remove-old-guilds Timer." }
            this.cancel()
          } else {
            val whitelist = app.koin.getProperty("BOT_GUILD_WHITELIST", "")
              .split(",")
              .filter(String::isNotEmpty)
              .map(String::toLong)

            BotUtils.leaveInactiveGuilds(jda, afterDays, whitelist)
          }
        }
    }

  HttpServer(bot, app.koin.getProperty("PORT", 8080))
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
