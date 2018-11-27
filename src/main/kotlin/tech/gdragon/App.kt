package tech.gdragon

import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.overriding
import fi.iki.elonen.NanoHTTPD
import mu.KotlinLogging
import tech.gdragon.db.initializeDatabase
import tech.gdragon.discord.BotConfig
import tech.gdragon.discord.DataStoreConfig
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
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
      val config = loadConfiguration()

      val dataDirectory = config[appData]
      val databaseName = config[appDatabase]

      // Connect to database
      initializeDatabase("$dataDirectory/$databaseName")

      val dataStoreConfig = DataStoreConfig(
        bucketId = config[B2.bucket_id],
        bucketName = config[B2.bucket_name],
        accountId = config[B2.account_id],
        accountKey = config[B2.account_key],
        baseUrl = config[B2.base_url]
      )

      val botConfig = BotConfig(
        token = config[Bot.token],
        version = config[appVersion],
        website = config[appWebsite],
        datastore = dataStoreConfig
      )

      val discordBot = DiscordBot(botConfig)

      val botServer = App(
        port = config[appPort],
        inviteUrl = discordBot.api.asBot().getInviteUrl(DiscordBot.PERMISSIONS)
      )

      initializeDataDirectory(dataDirectory)

      try {
        logger.info("Starting HTTP Server: http://localhost:${config[appPort]}")
        botServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
      } catch (e: IOException) {
        e.printStackTrace()
      }
    }

    /**
     * Creates the data directory and cleans up any remnant MP3 files in there
     */
    private fun initializeDataDirectory(dataDirectory: String) {
      try {
        val recordingsDir = "$dataDirectory/recordings/"
        logger.info("Creating recordings directory: {}", recordingsDir)
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

    private fun loadConfiguration(): Configuration {
      return ConfigurationProperties.systemProperties() overriding
        EnvironmentVariables() overriding
        ConfigurationProperties.fromResource("defaults.properties")
    }
  }
}
