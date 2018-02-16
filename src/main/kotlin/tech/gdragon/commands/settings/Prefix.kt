package tech.gdragon.commands.settings

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.Command
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild

class Prefix : Command {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.size == 1) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    transaction {
      val guild = Guild.findById(event.guild.idLong)
      val newPrefix = args.first()

      val message =
        guild?.settings?.let {
          it.prefix = newPrefix
          "Command prefix now set to ${it.prefix}."
        } ?: "Could not set to prefix $newPrefix."

      BotUtils.sendMessage(event.channel, message)
    }
  }

  override fun usage(prefix: String): String = "${prefix}prefix [character]"

  override fun description(): String = "Sets the prefix for each command to avoid conflict with other bots (Default is '!')"
}
