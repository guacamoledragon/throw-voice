package tech.gdragon

import fi.iki.elonen.NanoHTTPD
import mu.KotlinLogging
import tech.gdragon.db.Shim
import tech.gdragon.db.removeAncientGuilds
import tech.gdragon.discord.Bot
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate

class App private constructor(port: Int, val clientId: String, val inviteUrl: String) : NanoHTTPD(port) {
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
      val token = System.getenv("BOT_TOKEN")
      val port = System.getenv("PORT")
      val clientId = System.getenv("CLIENT_ID") // TODO: Can we get rid of this w/o consequences?
      val dataDirectory = System.getenv("DATA_DIR")

      // Connect to database
      Shim.initializeDatabase("$dataDirectory/settings.db")

      val bot = Bot(token)
      val inviteUrl = bot.api.asBot().getInviteUrl(Bot.PERMISSIONS)
      val app = App(Integer.parseInt(port), clientId, inviteUrl)

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
          removeAncientGuilds()
        }

      try {
        logger.info { "Starting HTTP Server: http://localhost:$port" }
        app.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
      } catch (e: IOException) {
        e.printStackTrace()
      }
    }
  }
}
