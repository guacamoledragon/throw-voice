package tech.gdragon

import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.overriding
import fi.iki.elonen.NanoHTTPD
import mu.KotlinLogging
import tech.gdragon.db.initializeDatabase
import tech.gdragon.discord.BotConfig
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate
import tech.gdragon.discord.Bot as DiscordBot

class App private constructor(port: Int, val inviteUrl: String) : NanoHTTPD(port) {
  override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
    val uri = session.uri

    return when (uri.toLowerCase()) {
      "/ping" -> {
        val response = newFixedLengthResponse("pong")
        response
      }
      else -> {
        val response = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "")
        response.addHeader("Location", inviteUrl)
        response
      }
    }
  }

  companion object {

    private val logger = KotlinLogging.logger { }

    /**
     * Starts a simple HTTP Service, whose only response is to redirect to the bot's page.
     */
    @JvmStatic
    fun main(args: Array<String>) {
      val config = configuration()

      val dataDirectory = config[appData]
      val databaseName = config[appDatabase]

      // Connect to database
      initializeDatabase("$dataDirectory/$databaseName")

      val botConfig = BotConfig(
        token = config[Bot.token]
      )

      val discordBot = DiscordBot(botConfig)

      val botServer = App(
        port = config[appPort],
        inviteUrl = discordBot.api.asBot().getInviteUrl(DiscordBot.PERMISSIONS)
      )

      try {
        val recordingsDir = "$dataDirectory/recordings/"
        logger.info { "Creating recordings directory: $recordingsDir" }
        Files.createDirectories(Paths.get(recordingsDir))
      } catch (e: IOException) {
        logger.error(e) { "Could not create recordings directory" }
      }

      logger.info { "Start background process to remove unused Guilds." }
      Timer("remove-old-guilds", true)
        .scheduleAtFixedRate(0L, Duration.ofDays(1L).toMillis()) {
          BotUtils.leaveAncientGuilds(discordBot.api)
        }

      try {
        logger.info("Starting HTTP Server: http://localhost:${config[appPort]}")
        botServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
      } catch (e: IOException) {
        e.printStackTrace()
      }
    }

    private fun configuration(): Configuration {
      return ConfigurationProperties.systemProperties() overriding
        EnvironmentVariables() overriding
        ConfigurationProperties.fromResource("defaults.properties")
    }
  }
}
