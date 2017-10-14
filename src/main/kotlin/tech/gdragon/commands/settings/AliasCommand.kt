package tech.gdragon.commands.settings

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.Command
import tech.gdragon.commands.CommandHandler
import tech.gdragon.db.dao.Alias
import tech.gdragon.db.dao.Guild

class AliasCommand : Command {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    // Argument count must be two
    require(args.size == 2) {
      val prefix = transaction { Guild.findById(event.guild.idLong)!!.settings.prefix }
      BotUtils.sendMessage(event.channel, usage(prefix))
    }

    val channel = event.channel
    val command = args[0].toLowerCase()

    // Checks that command to alias exists
    if (!CommandHandler.commands.containsKey(command)) {
      BotUtils.sendMessage(channel, "Command '$command' not found.")
    } else {
      val aliases = transaction { Guild.findById(event.guild.idLong)!!.settings.aliases.toList() }
      val alias = args[1].toLowerCase()

      // Checks that alias doesn't already exist
      if (aliases.find { it.name == alias } != null) {
        BotUtils.sendMessage (channel, "Alias '$alias' already exists.")
      } else {
        transaction {
          Guild.findById(event.guild.idLong)?.settings?.let {
            Alias.new {
              name = command
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
