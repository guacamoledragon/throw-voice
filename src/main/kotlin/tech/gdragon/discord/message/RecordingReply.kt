package tech.gdragon.discord.message

import dev.minn.jda.ktx.interactions.components.link
import dev.minn.jda.ktx.interactions.components.row
import dev.minn.jda.ktx.messages.Embed
import dev.minn.jda.ktx.messages.MessageCreate
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.emoji.Emoji
import org.apache.commons.io.FileUtils
import tech.gdragon.db.dao.Recording
import tech.gdragon.db.table.Tables
import java.awt.Color

class RecordingReply(recording: Recording, appBaseUrl: String) {
  private val guildId = recording.readValues[Tables.Recordings.guild].value
  private val sessionId = recording.id.value
  private val createdOn = formatInstant(recording.createdOn)
  private val duration = recording.pseudoDuration().toMinutes().toInt()
  private val size = FileUtils.byteCountToDisplaySize(recording.size)
  private val appRecordingUrl = "$appBaseUrl/v1/recordings?guild=$guildId&session-id=$sessionId"
  private val recordingUrl = recording.url?.let { url ->
    if (EmbedBuilder.URL_PATTERN.matcher(url).matches())
      recording.url
    else
      null
  }

  private val voteUrl = "https://top.gg/bot/pawa/vote"

  val embed = Embed {
    title = sessionId
    description = "Here's your recording, enjoy!"
    color = Color.decode("#596800").rgb
    url = recordingUrl
    field {
      name = "Created On"
      value = createdOn
      inline = false
    }
    field {
      name = "Duration"
      value = "${if (duration == 0) "???" else duration.toString()} minutes"
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
