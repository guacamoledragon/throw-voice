package tech.gdragon

import fi.iki.elonen.NanoHTTPD
import tech.gdragon.discord.Bot

class HttpServer(bot: Bot, val port: Int = 8080) {
  val inviteUrl: String = bot.api().getInviteUrl(Bot.PERMISSIONS)

  val server = object : NanoHTTPD(port) {
    override fun serve(session: IHTTPSession): Response {
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
