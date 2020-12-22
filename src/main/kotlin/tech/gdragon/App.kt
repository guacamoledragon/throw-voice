@file:JvmName("App")

package tech.gdragon

import mu.KotlinLogging
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module
import org.koin.ext.getIntProperty
import tech.gdragon.data.DataStore
import tech.gdragon.db.Database
import tech.gdragon.db.EmbeddedDatabase
import tech.gdragon.db.RemoteDatabase
import tech.gdragon.discord.Bot
import tech.gdragon.repl.REPL
import java.io.IOException
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
          fileProperties("/dev.properties") // TODO: Remove this dev.properties
        }
      }
    // TODO: Add function `externalFileProperties` that allows for loading properties from a file
    environmentProperties()

    val databaseModule = module {
      single<Database> {
        if (getProperty("BOT_STANDALONE").toBoolean())
          EmbeddedDatabase("${getProperty("BOT_DATA_DIR", "./")}/embedded-database")
        else
          RemoteDatabase(
            getProperty("DB_NAME"),
            getProperty("DB_HOST"),
            getProperty("DB_USER"),
            getProperty("DB_PASSWORD")
          )
      }
    }
    modules(
      module {
        single { Bot() }
        single { DataStore() }
        single { REPL() }
      },
      databaseModule
    )
  }

  val dataDir = app.koin.getProperty("BOT_DATA_DIR", "./")
  initializeDataDirectory(dataDir)

  val db = app.koin.get<Database>()

  shutdownHook(db)

  val bot =
    Bot().also {
      logger.info("Starting background process to remove unused Guilds.")
      Timer("remove-old-guilds", true)
        .scheduleAtFixedRate(0L, Duration.ofDays(1L).toMillis()) {
          val jda = it.api()
          val afterDays = app.koin.getIntProperty("BOT_LEAVE_GUILD_AFTER", 30)

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

  REPL()
    .also {
      it.nRepl["bot"] = bot
      it.nRepl["db"] = db
    }

  HttpServer(bot, app.koin.getIntProperty("BOT_HTTP_PORT", 8080))
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

fun shutdownHook(db: Database) {
  Runtime.getRuntime().addShutdownHook(Thread() {
    logger.info { "Shutting down..." }
    db.stop()
  })
}
