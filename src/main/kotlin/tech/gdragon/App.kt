package tech.gdragon

import fi.iki.elonen.NanoHTTPD
import org.slf4j.LoggerFactory
import tech.gdragon.db.Shim
import tech.gdragon.discord.Bot
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

class App private constructor(port: Int, val clientId: String, val inviteUrl: String) : NanoHTTPD(port) {

  override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
    val uri = session.uri

    val response: NanoHTTPD.Response

    if (uri.toLowerCase().contains("ping")) {
      response = NanoHTTPD.newFixedLengthResponse("pong")
    } else {
      response = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.REDIRECT, NanoHTTPD.MIME_HTML, "")
      response.addHeader("Location", inviteUrl)
    }

    return response
  }

  companion object {

    private val logger = LoggerFactory.getLogger(App::class.java)

    /**
     * Starts a simple HTTP Service, whose only response is to redirect to the bot's page.
     */
    @JvmStatic
    fun main(args: Array<String>) {
      val token = System.getenv("BOT_TOKEN")
      val port = System.getenv("PORT")
      val clientId = System.getenv("CLIENT_ID")
      val dataDirectory = System.getenv("DATA_DIR")

      // Connect to database
      Shim.initializeDatabase(dataDirectory + "/settings.db")

//      val bot = Bot(token)
//      val inviteUrl = bot.api.asBot().getInviteUrl(Bot.PERMISSIONS)
      val app = App(Integer.parseInt(port), clientId, inviteUrl = "")

      try {
        val recordingsDir = dataDirectory + "/recordings/"
        logger.info("Creating recordings directory: {}", recordingsDir)
        Files.createDirectories(Paths.get(recordingsDir))
      } catch (e: IOException) {
        logger.error("Could not create recordings directory", e)
      }

      try {
        logger.info("Starting HTTP Server: http://localhost:" + port)
        app.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
      } catch (e: IOException) {
        e.printStackTrace()
      }
    }
  }
}
