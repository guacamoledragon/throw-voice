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
import java.time.Duration

class RecordingReply(recording: Recording, appBaseUrl: String) {
  private val guildId = recording.readValues[Tables.Recordings.guild].value
  private val sessionId = recording.id.value
  private val createdOn = formatShortDateTime(recording.createdOn)
  private val expiresOn = formatRelativeTime(recording.createdOn.plus(Duration.ofDays(1L)))
  private val speakers = recording.speakers.joinToString { it.asMention }.ifBlank { "N/A" }
  private val duration = recording.duration.let {
    if (it.isZero) {
      "???"
    } else {
      val hours: Long = it.toHours()
      val minutes: Int = it.toMinutesPart()
      val seconds: Int = it.toSecondsPart()
      String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
  }
  private val size = FileUtils.byteCountToDisplaySize(recording.size)
  private val appRecordingUrl = "$appBaseUrl/v1/recordings?guild=$guildId&session-id=$sessionId"
  private val recordingUrl = recording.url.orEmpty()

  private val voteUrl = "https://top.gg/bot/pawa/vote"

  val embed = Embed {
    title = "Session ID: `$sessionId`"
    description = "Here's your recording, enjoy!"
    color = Color.decode("#596800").rgb
    field {
      name = "Created On"
      value = createdOn
      inline = true
    }
    if (!recordingUrl.startsWith("file://")) {
      field {
        name = "Expires On"
        value = expiresOn
        inline = true
      }
    }
    field {
      name = "Speakers"
      value = speakers
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
      inline = true
    }
    // Only is a local link or Discord upload
    if (recordingUrl.startsWith("https://media.discordapp.net") || recordingUrl.startsWith("file://")) {
      field {
        name = "Recording Location"
        value = recordingUrl
        inline = false
      }
    }
  }

  val message = MessageCreate {
    embeds += embed
    components += row(
      link(appRecordingUrl, label = "View Recording", disabled = appBaseUrl.startsWith("discord://")),
      link(voteUrl, label = "Vote", emoji = Emoji.fromUnicode("❤"))
    )
  }
}
