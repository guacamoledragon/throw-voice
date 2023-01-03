package tech.gdragon.commands.settings

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang

class RemoveAlias : CommandHandler() {
  override fun action(args: Array<String>, event: MessageReceivedEvent, pawa: Pawa) {
    require(args.size == 1) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val alias = args.first().lowercase()
    val aliasDeleted = transaction {
      Guild
        .findById(event.guild.idLong)
        ?.settings
        ?.aliases
        ?.find { it.alias == alias }
        ?.run { delete(); true }
    } ?: false

    val translator = transaction { Guild[event.guild.idLong].settings.language.let(Babel::removealias) }

    val message =
      if (aliasDeleted) ":dancer: _${translator.remove(alias)}_"
      else ":no_entry_sign: _${translator.doesNotExist(alias)}_"

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
    BotUtils.sendMessage(defaultChannel, message)
  }

  override fun usage(prefix: String, lang: Lang): String = Babel.removealias(lang).usage(prefix)

  override fun description(lang: Lang): String = "Removes an alias for a command."
}
