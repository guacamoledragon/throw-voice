package tech.gdragon.commands.settings

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.choice
import dev.minn.jda.ktx.interactions.commands.option
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang

object Language {
  val command = Command("lang", "Choose the language of the messages given.") {
    option<String>("language", "Choose your language", true) {
      Lang
        .entries
        .map { Pair(it.locale.displayName, it.name) }
        .forEach { choice(it.first, it.second) }
    }
  }

  fun slashHandler(pawa: Pawa): suspend CoroutineEventListener.(GenericCommandInteractionEvent) -> Unit = { event ->
    event.guild?.let {
      val locale = event.getOption("language")!!.asString
      val message = handler(pawa, it.idLong, locale)
      event.reply(message).await()
    } ?: event.reply(":no_entry: _${Babel.slash(Lang.EN).inGuild}").await()
  }

  fun handler(pawa: Pawa, guildId: Long, newLocale: String): String = pawa
    .setLocale(guildId, newLocale)
    .let { "${it.first.flagEmoji} :arrow_right: ${it.second.flagEmoji}" }
}
