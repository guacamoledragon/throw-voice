package tech.gdragon.commands.slash

import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.components.getOption
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import tech.gdragon.api.pawa.Pawa

object Recover {
  val command = Command("recover", "Recover a recording that failed to upload.") {
    option<String>("session-id", "The Session ID of the failed upload.", required = true)
  }

  fun slashHandler(pawa: Pawa): suspend CoroutineEventListener.(GenericCommandInteractionEvent) -> Unit = { event ->
    println("Do nothing.")

    event
      .reply(event.getOption<String>("session-id")!!)
      .queue()
  }
}
