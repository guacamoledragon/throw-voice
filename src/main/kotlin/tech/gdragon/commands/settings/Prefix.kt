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

class Prefix : CommandHandler() {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent, pawa: Pawa) {
    require(args.size == 1) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val newPrefix = args.first()
    val prefix = BotUtils.setPrefix(event.guild, newPrefix)

    val translator = transaction { Guild[event.guild.idLong].settings.language.let(Babel::prefix) }

    val message =
      if (prefix == newPrefix) ":twisted_rightwards_arrows: _${translator.changed(prefix)}_"
      else ":no_entry_sign: _${translator.notChanged(newPrefix)}_"

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
    BotUtils.sendMessage(defaultChannel, message)
  }

  override fun usage(prefix: String, lang: Lang): String = Babel.prefix(lang).usage(prefix)

  override fun description(lang: Lang): String =
    "Sets the prefix for each command to avoid conflict with other bots (Default is '!')"
}
