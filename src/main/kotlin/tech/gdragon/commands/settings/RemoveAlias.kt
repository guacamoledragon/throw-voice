package tech.gdragon.commands.settings

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild

class RemoveAlias : CommandHandler() {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.size == 1) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val alias = args.first().toLowerCase()
    val aliasDeleted = transaction {
      Guild
        .findById(event.guild.idLong)
        ?.settings
        ?.aliases
        ?.find { it.alias == alias }
        ?.run { delete(); true }
    } ?: false

    val message =
      if (aliasDeleted) ":dancer: _Alias **`$alias`** has been removed._"
      else ":no_entry_sign: _Alias **`$alias`** does not exist._"

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
    BotUtils.sendMessage(defaultChannel, message)
  }

  override fun usage(prefix: String): String = "${prefix}removeAlias [alias name]"

  override fun description(): String = "Removes an alias for a command."
}
