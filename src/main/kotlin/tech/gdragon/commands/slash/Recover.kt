package tech.gdragon.commands.slash

import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.components.getOption
import dev.minn.jda.ktx.interactions.components.link
import dev.minn.jda.ktx.interactions.components.row
import dev.minn.jda.ktx.messages.Embed
import dev.minn.jda.ktx.messages.MessageCreate
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import org.apache.commons.io.FileUtils
import org.koin.core.context.GlobalContext
import tech.gdragon.BotUtils.trigoman
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.data.Datastore
import tech.gdragon.db.dao.Recording
import tech.gdragon.discord.message.RequestAccessModal
import tech.gdragon.koin.getStringProperty
import java.awt.Color

class RecordingReply(recording: Recording) {
  val embed = Embed {
    title = recording.id.value
    description = "Attempted to recover recording, and this is what we found."
    color = Color.decode("#596800").rgb
    field {
      name = "Created On"
      value = recording.createdOn.toString()
      inline = false
    }
    field {
      name = "Duration"
      value = "${recording.pseudoDuration().toMinutes()} minutes"
      inline = true
    }
    field {
      name = "Size"
      value = FileUtils.byteCountToDisplaySize(recording.size)
    }
    field {
      name = "URL"
      value = recording.url!!
      inline = false
    }
  }

  val message = MessageCreate {
    embeds += embed
    components += row(
      link(recording.url!!, label = "Download"),
    )
  }
}

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

    val datastore = GlobalContext.get().get<Datastore>()
    val dataDirectory = GlobalContext.get().getStringProperty("BOT_DATA_DIR")

    event.deferReply().queue()
    val recording = pawa.recoverRecording(dataDirectory, datastore, event.guild?.idLong!!, sessionId)

    if (recording == null) {
      event.hook.sendMessage("Couldn't restore recording").queue()
    } else {
      val embed = RecordingReply(recording)

      event
        .hook
        .sendMessage(embed.message)
        .queue()
    }
  }
}
