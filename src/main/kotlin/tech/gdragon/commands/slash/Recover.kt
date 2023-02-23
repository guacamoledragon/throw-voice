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
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import org.apache.commons.io.FileUtils
import org.koin.core.context.GlobalContext
import tech.gdragon.BotUtils.trigoman
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.data.Datastore
import tech.gdragon.db.dao.Recording
import tech.gdragon.db.table.Tables
import tech.gdragon.discord.message.ErrorEmbed
import tech.gdragon.discord.message.RequestAccessModal
import tech.gdragon.koin.getStringProperty
import java.awt.Color

class RecordingReply(recording: Recording, appBaseUrl: String) {
  private val guildId = recording.readValues[Tables.Recordings.guild].value
  private val sessionId = recording.id.value
  private val createdOn = recording.createdOn.toString()
  private val duration = "${recording.pseudoDuration().toMinutes()} minutes"
  private val size = FileUtils.byteCountToDisplaySize(recording.size)
  private val appRecordingUrl = "$appBaseUrl/v1/recordings?guild=$guildId&session-id=$sessionId"
  private val voteUrl = "https://top.gg/bot/pawa/vote"

  val embed = Embed {
    title = sessionId
    description = "Here's your recording, enjoy!"
    color = Color.decode("#596800").rgb
    url = recording.url
    field {
      name = "Created On"
      value = createdOn
      inline = false
    }
    field {
      name = "Duration"
      value = duration
      inline = true
    }
    field {
      name = "Size"
      value = size
    }
  }

  val message = MessageCreate {
    embeds += embed
    components += row(
      link(appRecordingUrl, label = "View Recording"),
      link(voteUrl, label = "Vote", emoji = Emoji.fromUnicode("❤"))
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
