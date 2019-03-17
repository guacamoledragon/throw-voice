package tech.gdragon

import fi.iki.elonen.NanoHTTPD
import org.koin.core.KoinComponent
import org.koin.core.get
import org.koin.dsl.module
import tech.gdragon.discord.Bot

/**
 * Starts a simple HTTP Service, whose only response is to redirect to the bot's page.
 */
val httpServer = module {
  single {
    HttpServer()
  }
}

class HttpServer : KoinComponent {
  val port: Int = getKoin().getProperty("PORT", 8080)
  val bot: Bot = get()

  val inviteUrl: String = bot.api.asBot().getInviteUrl(Bot.PERMISSIONS)

  val server = object : NanoHTTPD(port) {
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
  }
}
