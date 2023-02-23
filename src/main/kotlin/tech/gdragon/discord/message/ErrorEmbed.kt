package tech.gdragon.discord.message

import dev.minn.jda.ktx.interactions.components.link
import dev.minn.jda.ktx.interactions.components.row
import dev.minn.jda.ktx.messages.Embed
import dev.minn.jda.ktx.messages.MessageCreate
import java.awt.Color

class ErrorEmbed(
  val title: String = "Error",
  val description: String = "Something went wrong. Please contact support."
) {
  val supportServerUrl = "https://discord.gg/gkvsNw8"

  val embed = Embed(description, title, color = Color.RED.rgb)

  val message = MessageCreate {
    embeds += embed
    components += row(
      link(supportServerUrl, label = "Support Server"),
    )
  }
}
