@file:JvmName("App")

package tech.gdragon

import mu.KotlinLogging
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module
import tech.gdragon.data.DataStore
import tech.gdragon.db.initializeDatabase
import tech.gdragon.discord.Bot
import tech.gdragon.metrics.Rollbar
import java.io.IOException
import java.time.Duration
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate

val logger = KotlinLogging.logger { }

fun main() {
  shutdownHook()

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
        single { Rollbar() }
      }
    )
  }

  val dataDir = app.koin.getProperty("DATA_DIR", "./")
  initializeDataDirectory(dataDir)
  app.koin.apply {
    initializeDatabase(getProperty("DB_NAME"), getProperty("DB_HOST"), getProperty("DB_USER"), getProperty("DB_PASSWORD"))
  }

  val bot =
    Bot().also {
      logger.info("Starting background process to remove unused Guilds.")
      Timer("remove-old-guilds", true)
        .scheduleAtFixedRate(0L, Duration.ofDays(1L).toMillis()) {
          val jda = it.api()
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
 * Creates the data directory
 */
private fun initializeDataDirectory(dataDirectory: String) {
  try {
    val recordingsDir = "$dataDirectory/recordings/"
    logger.info("Creating recordings directory: $recordingsDir")
  } catch (e: IOException) {
    logger.error("Could not create recordings directory", e)
  }
}

fun shutdownHook() {
  Runtime.getRuntime().addShutdownHook(Thread() {
    val stacktraces = Thread.getAllStackTraces()
    stacktraces.forEach { (t, stacktrace) ->
      logger.error {
        "${t.name}: ${stacktrace.joinToString("\n")}"
      }
    }
  })
}
