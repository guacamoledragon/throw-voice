@file:JvmName("App")

package tech.gdragon

import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import org.koin.core.KoinApplication
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module
import org.koin.environmentProperties
import org.koin.fileProperties
import org.koin.logger.SLF4JLogger
import tech.gdragon.data.Datastore
import tech.gdragon.data.LocalDatastore
import tech.gdragon.data.S3Datastore
import tech.gdragon.db.Database
import tech.gdragon.discord.Bot
import tech.gdragon.koin.getBooleanProperty
import tech.gdragon.koin.getStringProperty
import tech.gdragon.koin.overrideFileProperties
import tech.gdragon.metrics.EventTracer
import tech.gdragon.metrics.Honey
import tech.gdragon.metrics.NoHoney
import tech.gdragon.repl.REPL
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate

object App {
  private val logger = KotlinLogging.logger { }

  lateinit var app: KoinApplication

  @JvmStatic
  fun start(): KoinApplication {
    val app = startKoin {
      logger(SLF4JLogger(Level.INFO))
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
      val overrideFile = System.getenv("OVERRIDE_FILE")
      if (overrideFile.isNullOrEmpty()) {
        logger.info { "No override file provided. Please set OVERRIDE_FILE environment variable if desired." }
      } else {
        overrideFileProperties(overrideFile)
      }

      val isStandalone = koin.getBooleanProperty("BOT_STANDALONE")

      val datastoreModule = module {
        single<Datastore>(createdAtStart = true) {
          val bucketName = getProperty<String>("DS_BUCKET")
          if (isStandalone) {
            LocalDatastore(bucketName)
          } else {
            val accessKey = getProperty<String>("DS_ACCESS_KEY")
            val secretKey = getProperty<String>("DS_SECRET_KEY")
            val endpoint = getProperty<String>("DS_HOST")
            val region = getProperty<String>("DS_REGION")
            val baseUrl = getProperty<String>("DS_BASEURL")
            S3Datastore(
              accessKey,
              secretKey,
              endpoint,
              region,
              bucketName,
              baseUrl
            )
          }
        }
      }

      val optionalModules = module {
        val createdAtStart = !isStandalone
        single(createdAtStart = createdAtStart) {
          REPL()
        }
        single<EventTracer>(createdAtStart = true) {
          if (isStandalone)
            NoHoney()
          else
            Honey(getProperty("TRACING_API_KEY"))
        }
        single<Tracer>(createdAtStart = true) {
          val scopeName = "tech.gdragon.pawa"
          if (isStandalone)
            OpenTelemetry.noop().getTracer(scopeName)
          else {
            GlobalOpenTelemetry.get().getTracer(scopeName)
          }
        }
      }
      val modules = listOf(
        Database.module(isEmbedded = isStandalone),
        module {
          single { Bot(getProperty("BOT_TOKEN"), get()) }
        },
        datastoreModule,
        optionalModules
      )
      koin.loadModules(modules)
    }
    val dataDir = app.koin.getStringProperty("BOT_DATA_DIR")
    initializeDataDirectory(dataDir)
    shutdownHook()

    app.koin.get<Bot>()
      .let {
        logger.info { "Starting background process to remove unused Guilds." }
        Timer("remove-old-guilds", true)
          .scheduleAtFixedRate(0L, Duration.ofDays(1L).toMillis()) {
            val jda = it.api()
            val afterDays = app.koin.getProperty("BOT_LEAVE_GUILD_AFTER", "30").toLong()

            if (afterDays <= 0) {
              logger.info { "Disabling remove-old-guilds Timer." }
              this.cancel()
            } else {
              val whitelist = app.koin.getProperty("BOT_GUILD_WHITELIST", "")
                .split(",")
                .filter(String::isNotEmpty)
                .map(String::toLong)

              val inactiveGuilds = BotUtils.leaveInactiveGuilds(jda, afterDays, whitelist)

              app.koin.get<EventTracer>().sendEvent(mapOf("inactive-guilds" to inactiveGuilds))
            }
          }
        logger.info {
          "Invite URL: ${it.api().setRequiredScopes("applications.commands").getInviteUrl(Bot.PERMISSIONS)}"
        }
      }

    return app
  }

  @JvmStatic
  fun stop(app: KoinApplication) {
    app.koin.run {
      getOrNull<Database>()?.shutdown()
      getOrNull<EventTracer>()?.shutdown()
      getOrNull<Bot>()?.shutdown()

      if (getProperty<String>("BOT_STANDALONE").toBoolean().not()) {
        getOrNull<REPL>()?.shutdown()
        getOrNull<Datastore>()?.shutdown()
      }
    }

    app.close()
    GlobalContext.stopKoin()
  }

  /**
   * Creates the data directory
   */
  @JvmStatic
  fun initializeDataDirectory(dataDirectory: String) {
    try {
      val recordingsDir = Paths.get("$dataDirectory/recordings/")
      logger.info {
        "Creating recordings directory: $recordingsDir"
      }
      Files.createDirectories(recordingsDir)
    } catch (e: IOException) {
      logger.error(e) {
        "Could not create recordings directory"
      }
    }
  }

  private fun shutdownHook() {
    Runtime.getRuntime().addShutdownHook(Thread {
      logger.info { "Shutting down..." }
    })
  }

  @JvmStatic
  fun main(args: Array<String>) {
    app = start()
  }
}
