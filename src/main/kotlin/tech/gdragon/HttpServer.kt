package tech.gdragon

import fi.iki.elonen.NanoHTTPD
import tech.gdragon.discord.Bot

class HttpServer(bot: Bot, val port: Int) {
  val inviteUrl: String = bot.api().getInviteUrl(Bot.PERMISSIONS)

  val server = object : NanoHTTPD(port) {
    override fun serve(session: IHTTPSession): Response {
      val uri = session.uri

      return when (uri.toLowerCase()) {
        "/ping" -> {
          newFixedLengthResponse("pong")
        }
        "/invite" -> {
          newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "")
            .apply { addHeader("Location", inviteUrl) }
        }
        else -> {
          newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Error 404, file not found.")
        }
      }
    }
  }
}
