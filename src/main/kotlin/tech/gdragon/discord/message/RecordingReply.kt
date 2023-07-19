package tech.gdragon.discord.message

import dev.minn.jda.ktx.interactions.components.link
import dev.minn.jda.ktx.interactions.components.row
import dev.minn.jda.ktx.messages.Embed
import dev.minn.jda.ktx.messages.MessageCreate
import net.dv8tion.jda.api.entities.emoji.Emoji
import org.apache.commons.io.FileUtils
import tech.gdragon.db.dao.Recording
import tech.gdragon.db.table.Tables
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
