package tech.gdragon.commands.slash

import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.components.getOption
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import org.koin.core.context.GlobalContext
import tech.gdragon.BotUtils.trigoman
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.data.Datastore
import tech.gdragon.discord.message.ErrorEmbed
import tech.gdragon.discord.message.RecordingReply
import tech.gdragon.discord.message.RequestAccessModal
import tech.gdragon.koin.getStringProperty

object Recover {
  val command = Command("recover", "Recover a recording that failed to upload.") {
    restrict(guild = true)
    option<String>("session-id", "The Session ID of the failed upload.", required = true, autocomplete = true)
  }

  fun slashHandler(pawa: Pawa): suspend CoroutineEventListener.(GenericCommandInteractionEvent) -> Unit = { event ->
    require(trigoman == event.user.idLong) {
      val modal = RequestAccessModal("Request access to /recover command").modal
      event.replyModal(modal).queue()
    }

    val sessionId = event.getOption<String>("session-id")!!

    val koin = GlobalContext.get()
    val datastore = koin.get<Datastore>()
    val dataDirectory = koin.getStringProperty("BOT_DATA_DIR")
    val appUrl = koin.getStringProperty("APP_URL")

    val guildId = event.guild!!.idLong

    // Reply to the user, the upcoming request requires database interaction
    event.deferReply().queue()
    val recording = pawa.recoverRecording(dataDirectory, datastore, guildId, sessionId)

    val interaction =
      if (recording == null) {
        val errorEmbed = ErrorEmbed("Recording Not Found", "Couldn't find recording with Session ID: $sessionId")
        event
          .hook
          .sendMessage(errorEmbed.message)
      } else {
        val embed = RecordingReply(recording, appUrl)

        event
          .hook
          .sendMessage(embed.message)
      }

    interaction.queue()
  }
}
