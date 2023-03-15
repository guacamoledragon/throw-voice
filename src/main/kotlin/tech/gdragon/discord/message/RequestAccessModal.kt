package tech.gdragon.discord.message

import dev.minn.jda.ktx.messages.Embed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal
import java.awt.Color

class RequestAccessModal(val title: String) {

  val request = TextInput.create("request-body", "Request", TextInputStyle.PARAGRAPH)
    .setPlaceholder("Explain why you want access to this feature?")
    .setMaxLength(1000)
    .build()

  val modal = Modal
    .create("request-access", "Request Access")
    .setTitle(title)
    .addComponents(ActionRow.of(request))
    .build()
}

class RequestAccessReply(val user: User, val request: String, val sessionId: String) {
  val embed = Embed {
    title = "Requesting Command Access"
    description = request
    color = Color.decode("#596800").rgb
    field {
      name = "Session ID"
      value = sessionId
      inline = false
    }
    field {
      name = "User"
      value = user.asTag
    }
  }
}
