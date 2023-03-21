package tech.gdragon.commands.settings

import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.choice
import dev.minn.jda.ktx.interactions.commands.option
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang

class Language : CommandHandler() {
  companion object {
    val command = Command("lang", "Choose the language of the messages given.") {
      option<String>("language", "Choose your language", true) {
        Lang
          .values()
          .map { Pair(it.locale.displayName, it.name) }
          .forEach { choice(it.first, it.second) }
      }
    }

    fun slashHandler(pawa: Pawa): suspend CoroutineEventListener.(GenericCommandInteractionEvent) -> Unit = { event ->
      event.guild?.let {
        val locale = event.getOption("language")!!.asString
        val message = handler(pawa, it.idLong, locale)
        event.reply(message).queue()
      } ?: event.reply(":no_entry: _${Babel.slash(Lang.EN).inGuild}").queue()
    }

    fun handler(pawa: Pawa, guildId: Long, newLocale: String): String = pawa
      .setLocale(guildId, newLocale)
      .let { "${it.first.flagEmoji} :arrow_right: ${it.second.flagEmoji}" }
  }

  override fun action(args: Array<String>, event: MessageReceivedEvent, pawa: Pawa) {
    val lang = args.firstOrNull()?.uppercase() ?: ""

    require(args.isNotEmpty() && Babel.valid(lang)) {
      throw InvalidCommand(::usage, "Expected one of: ${Lang.values().joinToString { it.name }}, but got $lang")
    }

    val message = handler(pawa, event.guild.idLong, lang)

    BotUtils.sendMessage(event.channel, message)
  }

  override fun usage(prefix: String, lang: Lang): String {
    val translator = Babel.language(lang)
    return translator.usage(prefix)
  }

  override fun description(lang: Lang): String = "Specifies the language to use."
}
