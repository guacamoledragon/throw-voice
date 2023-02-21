package tech.gdragon.discord.message

import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal

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
