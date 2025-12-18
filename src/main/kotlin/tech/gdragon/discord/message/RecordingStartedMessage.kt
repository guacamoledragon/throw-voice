package tech.gdragon.discord.message

import dev.minn.jda.ktx.interactions.components.row
import dev.minn.jda.ktx.interactions.components.secondary
import dev.minn.jda.ktx.messages.Embed
import dev.minn.jda.ktx.messages.MessageCreate
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang
import java.awt.Color

/**
 * Message sent when a recording starts, containing an embed with session info
 * and a "Recover Recording" button that users can click to trigger recovery.
 */
class RecordingStartedMessage(channelId: String, sessionId: String, lang: Lang) {
  companion object {
    const val BUTTON_ID_PREFIX = "recover"
  }

  private val resource = Babel.resource(lang)
  private val recordingText = resource.getString("record.recording").format("<#$channelId>")
  private val warningText = resource.getString("record.warning")

  val embed = Embed {
    title = ":red_circle: $recordingText"
    color = Color.GREEN.rgb
    field {
      name = "Session ID"
      value = "`$sessionId`"
      inline = false
    }
    field {
      name = ":warning: Warning"
      value = "_${warningText}_"
      inline = false
    }
  }

  val component = row(
    secondary("$BUTTON_ID_PREFIX:$sessionId", label = "Recover")
  )

  val message = MessageCreate {
    embeds += embed
    components += component
  }
}
