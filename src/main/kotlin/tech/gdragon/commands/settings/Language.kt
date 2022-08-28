package tech.gdragon.commands.settings

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang

class Language : CommandHandler() {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent, pawa: Pawa) {
    val lang = args.firstOrNull()?.uppercase() ?: ""

    require(args.isNotEmpty() && Babel.valid(lang)) {
      throw InvalidCommand(::usage, "Expected one of: ${Lang.values().joinToString { it.name }}, but got $lang")
    }

    val (prevLang, newLang) = transaction {
      val guild = Guild[event.guild.idLong]
      val prev = guild.settings.language
      val newLang = Lang.valueOf(lang)

      guild.settings.language = newLang

      Pair(prev.flagEmoji, newLang.flagEmoji)
    }

    BotUtils.sendMessage(event.channel, "$prevLang :arrow_right: $newLang")
  }

  override fun usage(prefix: String, lang: Lang): String {
    val translator = Babel.language(lang)
    return translator.usage(prefix)
  }

  override fun description(lang: Lang): String = "Specifies the language to use."
}
