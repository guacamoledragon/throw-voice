package tech.gdragon.commands.settings

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Alias
import tech.gdragon.db.dao.Guild
import tech.gdragon.discord.Command

class Alias : CommandHandler {
  companion object {
    val deprecationMap = mapOf(
      "join" to "record",
      "info" to "help",
      "leave" to "stop",
      "symbol" to "prefix"
    )
  }

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.size == 2) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val channel = event.channel
    val command = args.first()

    // Checks that command to alias exists
    if (Command.values().none { it.name == command.toUpperCase() }) {
      BotUtils.sendMessage(channel, "Command '$command' not found.")
    } else {
      val aliases = transaction { Guild.findById(event.guild.idLong)?.settings?.aliases?.toList() }
      val alias = args[1]

      // Checks that alias doesn't already exist
      if (aliases?.any { it.name == alias } == true) {
        BotUtils.sendMessage(channel, "Alias '$alias' already exists.")
      } else {
        transaction {
          Guild.findById(event.guild.idLong)?.settings?.let {
            Alias.new {
              name = command.toUpperCase()
              this.alias = alias
              settings = it
            }

            BotUtils.sendMessage(channel, "New alias '$alias' set for command '$command'.")
          }
        }
      }
    }
  }

  override fun usage(prefix: String): String = "${prefix}alias [command name] [new command alias]"

  override fun description(): String = "Creates an alias, or alternate name, to a command for customization."
}
