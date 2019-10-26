package tech.gdragon.commands.settings

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild

class Prefix : CommandHandler() {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.size == 1) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    usageCounter.add(1)

    val newPrefix = args.first()
    val prefix = transaction {
      Guild
        .findById(event.guild.idLong)
        ?.settings
        ?.apply { prefix = newPrefix }
        ?.prefix
    }

    val message =
      if (prefix == newPrefix) ":twisted_rightwards_arrows: _Command prefix now set to **`${prefix}`**._"
      else ":no_entry_sign: _Could not set to prefix **`$newPrefix`**._"

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
    BotUtils.sendMessage(defaultChannel, message)
  }

  override fun usage(prefix: String): String = "${prefix}prefix [character]"

  override fun description(): String = "Sets the prefix for each command to avoid conflict with other bots (Default is '!')"
}
