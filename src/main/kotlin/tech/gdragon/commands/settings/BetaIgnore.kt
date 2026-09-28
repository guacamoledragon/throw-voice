package tech.gdragon.commands.settings

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.components.getOption
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import tech.gdragon.api.pawa.Pawa

object BetaIgnore {
  val command = Command("ignore", "Ignore audio from specified during User for current recording.") {
    option<User>("user", "The user to ignore", true)
  }

  fun handler(pawa: Pawa): suspend CoroutineEventListener.(GenericCommandInteractionEvent) -> Unit = { event ->
    val ignoreUser = event.getOption<User>("user")!!.idLong

    if (pawa.silenceUser(event.guild!!.idLong, ignoreUser)) {
      event.reply_("Ignoring user: <@$ignoreUser>").await()
    } else {
      event.reply_("Not connected, shoo!").await()
    }
  }
}
