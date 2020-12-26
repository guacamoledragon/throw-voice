@file:JvmName("App")

package tech.gdragon

import mu.KotlinLogging
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module
import tech.gdragon.data.DataStore
import tech.gdragon.db.Database
import tech.gdragon.db.EmbeddedDatabase
import tech.gdragon.db.RemoteDatabase
import tech.gdragon.discord.Bot
import tech.gdragon.koin.getStringProperty
import tech.gdragon.koin.overrideFileProperties
import tech.gdragon.repl.REPL
import java.io.IOException
import java.time.Duration
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate

val logger = KotlinLogging.logger { }

fun main() {
  val app = startKoin {
    printLogger(Level.INFO)
    /**
     * Default properties are here to set values that I want "baked" into the application whenever bundled.
     */
    fileProperties("/defaults.properties")

    /**
     * All configuration must come from environment variables, however this is trickier for people that don't deal with
     * computers at this level, hence we'll provide the option to use an override file.
     */
    environmentProperties()

    /**
     * If provided, the override file is a properties file that will override anything in the previous configurations.
     * When bundled, this should be where users are expected to interact with the settings.
     */
    System.getenv("OVERRIDE_FILE")?.let { overrideFile ->
      overrideFileProperties(overrideFile)
    }
      ?: if (koin.logger.isAt(Level.INFO)) koin.logger.info("No override file provided. Please set OVERRIDE_FILE environment variable if desired.")

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
        single(createdAtStart = true) {
          val endpoint = getProperty("DS_HOST")
          val bucketName = getProperty("DS_BUCKET")
          DataStore(
            getProperty("DS_ACCESS_KEY"),
            bucketName,
            endpoint,
            getProperty("DS_SECRET_KEY"),
            getProperty("DS_BASEURL", "$endpoint/$bucketName")
          )
        }
        single { HttpServer(get(), getProperty("BOT_HTTP_PORT").toInt()) }
      },
      databaseModule
    )
  }

  val dataDir = app.koin.getStringProperty("BOT_DATA_DIR")
  initializeDataDirectory(dataDir)

  val db = app.koin.get<Database>().also(::shutdownHook)

  val bot = app.koin.get<Bot>()
    .let {
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

  REPL()
    .let {
      it.nRepl["bot"] = bot
      it.nRepl["db"] = db
    }

  app.koin.get<HttpServer>().let {
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
