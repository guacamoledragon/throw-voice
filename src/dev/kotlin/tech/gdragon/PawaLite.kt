package tech.gdragon

import org.koin.java.KoinJavaComponent
import tech.gdragon.discord.Bot

fun main() {
  val app = App.start()
  val jda = KoinJavaComponent.get<Bot>(Bot::class.java).api()
  val inviteUrl = jda.setRequiredScopes("applications.commands").getInviteUrl(Bot.PERMISSIONS)
}
