package tech.gdragon

import fi.iki.elonen.NanoHTTPD
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import org.slf4j.LoggerFactory
import tech.gdragon.db.Shim
import tech.gdragon.discord.Bot
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

class App private constructor(port: Int, val clientId: String, val inviteUrl: String) : NanoHTTPD(port) {
  val client = OkHttpClient()
  val discordRollbarWebhook: String? = System.getenv("DISCORD_ROLLBAR_WEBHOOK")

  private fun discordWebhook(body: Map<String, String>): okhttp3.Response? {
    val postData = JSONObject(body["postData"])
    val eventName = postData["event_name"]
    val data = postData["data"] as JSONObject
    val itemBody: String =
      try {
        val item = data["item"] as JSONObject
        item.let {
          (((it["last_occurrence"] as JSONObject)["body"] as JSONObject)["message"] as JSONObject)["body"].toString()
        }
      } catch (e: JSONException) {
        ""
      }

    val requestBody = MultipartBody.Builder()
      .setType(MultipartBody.FORM)
      .addFormDataPart("content", itemBody)
      .build()

    val request = Request.Builder()
      .url(discordRollbarWebhook)
      .post(requestBody)
      .build()

    return client.newCall(request).execute()
  }

  override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
    val uri = session.uri

    return when (uri.toLowerCase()) {
      "/ping" -> {
        val response = newFixedLengthResponse("pong")
        response
      }
      "/rollbar" -> {
        val response =
          if (session.method == Method.POST && !discordRollbarWebhook.isNullOrEmpty()) {
            val body = mutableMapOf<String, String>()
            session.parseBody(body)
            discordWebhook(body)
          } else null

        val status = Response.Status.lookup(response?.code() ?: 500)
        val mime = response?.header("content-type") ?: MIME_PLAINTEXT
        val message = response?.message() ?: ""

        newFixedLengthResponse(status, mime, message)
      }
      else -> {
        val response = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "")
        response.addHeader("Location", inviteUrl)
        response
      }
    }
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
      val clientId = System.getenv("CLIENT_ID") // TODO: Can we get rid of this w/o consequences?
      val dataDirectory = System.getenv("DATA_DIR")

      // Connect to database
      Shim.initializeDatabase(dataDirectory + "/settings.db")

      val bot = Bot(token)
      val inviteUrl = bot.api.asBot().getInviteUrl(Bot.PERMISSIONS)
      val app = App(Integer.parseInt(port), clientId, inviteUrl)

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
