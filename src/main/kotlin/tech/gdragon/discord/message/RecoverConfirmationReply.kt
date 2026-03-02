package tech.gdragon.discord.message

import dev.minn.jda.ktx.interactions.components.link
import dev.minn.jda.ktx.interactions.components.primary
import dev.minn.jda.ktx.interactions.components.row
import dev.minn.jda.ktx.interactions.components.secondary
import dev.minn.jda.ktx.messages.Embed
import dev.minn.jda.ktx.messages.MessageCreate
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang
import java.awt.Color

/**
 * Ephemeral confirmation message shown when a user clicks the "Recover" button.
 * Explains what recovery does and provides Confirm / Cancel / Support Server buttons.
 */
class RecoverConfirmationReply(sessionId: String, lang: Lang) {
  companion object {
    const val CONFIRM_PREFIX = "recover-confirm"
    const val CANCEL_PREFIX = "recover-cancel"
    const val SUPPORT_SERVER_URL = "https://discord.gg/gkvsNw8"
  }

  private val resource = Babel.resource(lang)
  private val titleText = resource.getString("recover.confirm.title")
  private val descriptionText = resource.getString("recover.confirm.description").format(sessionId)
  private val warningText = resource.getString("recover.confirm.warning")

  val embed = Embed {
    title = titleText
    description = """
      $descriptionText
      
      **Please note:**
      $warningText
    """.trimIndent()
    color = Color.decode("#5865F2").rgb
  }

  val components = row(
    link(SUPPORT_SERVER_URL, label = "Support Server"),
    primary("$CONFIRM_PREFIX:$sessionId", label = "Confirm Recovery"),
    secondary("$CANCEL_PREFIX:$sessionId", label = "Cancel"),
  )

  val message = MessageCreate {
    embeds += embed
    components += this@RecoverConfirmationReply.components
  }
}
