@file:JvmName("App")

package tech.gdragon

import mu.KotlinLogging
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module
import tech.gdragon.data.Datastore
import tech.gdragon.data.LocalDatastore
import tech.gdragon.data.RemoteDatastore
import tech.gdragon.db.Database
import tech.gdragon.db.EmbeddedDatabase
import tech.gdragon.db.RemoteDatabase
import tech.gdragon.discord.Bot
import tech.gdragon.koin.getBooleanProperty
import tech.gdragon.koin.getStringProperty
import tech.gdragon.koin.overrideFileProperties
import tech.gdragon.repl.REPL
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

    val databaseModule = module(createdAtStart = true) {
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
    val datastoreModule = module {
      single<Datastore>(createdAtStart = true) {
        val bucketName = getProperty("DS_BUCKET")
        if (getProperty("BOT_STANDALONE").toBoolean()) {
          LocalDatastore(bucketName)
        } else {
          val endpoint = getProperty("DS_HOST")
          RemoteDatastore(
            getProperty("DS_ACCESS_KEY"),
            bucketName,
            endpoint,
            getProperty("DS_SECRET_KEY"),
            getProperty("DS_BASEURL", "$endpoint/$bucketName")
          )
        }
      }
    }
    val optionalModules = module {
      val createdAtStart = !koin.getBooleanProperty("BOT_STANDALONE")
      single(createdAtStart = createdAtStart) {
        REPL().also {
          it.nRepl["db"] = get<Database>()
          it.nRepl["bot"] = get<Bot>()
        }
      }
      single(createdAtStart = createdAtStart) {
        HttpServer(get(), getProperty("BOT_HTTP_PORT").toInt()).also {
          if (logger.isAt(Level.INFO)) {
            logger.info("Starting HTTP Server: http://localhost:${it.port}")
          }
          it.server.start()
        }
      }
    }
    modules(
      databaseModule,
      module {
        single { Bot() }
      },
      datastoreModule,
      optionalModules
    )
  }

  val dataDir = app.koin.getStringProperty("BOT_DATA_DIR")
  initializeDataDirectory(dataDir)
  shutdownHook()

  val bot = app.koin.get<Bot>()
    .let {
      logger.info("Starting background process to remove unused Guilds.")
      Timer("remove-old-guilds", true)
        .scheduleAtFixedRate(0L, Duration.ofDays(1L).toMillis()) {
          val jda = it.api()
          val afterDays = app.koin.getProperty("BOT_LEAVE_GUILD_AFTER", "30").toInt()

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
      logger.info {
        "Invite URL: ${it.api().getInviteUrl(Bot.PERMISSIONS)}"
      }
    }
}

/**
 * Creates the data directory
 */
private fun initializeDataDirectory(dataDirectory: String) {
  try {
    val recordingsDir = Paths.get("$dataDirectory/recordings/")
    logger.info("Creating recordings directory: $recordingsDir")
    Files.createDirectories(recordingsDir)
  } catch (e: IOException) {
    logger.error("Could not create recordings directory", e)
  }
}

fun shutdownHook() {
  Runtime.getRuntime().addShutdownHook(Thread() {
    logger.info { "Shutting down..." }
  })
}
