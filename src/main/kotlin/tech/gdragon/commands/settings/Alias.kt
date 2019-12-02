package tech.gdragon.commands.settings

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.asyncTransaction
import tech.gdragon.db.dao.Alias
import tech.gdragon.db.dao.Guild
import tech.gdragon.discord.Command

class Alias : CommandHandler() {

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.size == 2) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    usageCounter.add(1)

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
    val command = args.first().toUpperCase()

    // Checks that command to alias exists
    if (Command.ALIAS.name == command || Command.values().none { it.name == command }) {
      BotUtils.sendMessage(defaultChannel, ":no_entry_sign: _Invalid command: **`${command.toLowerCase()}`**_")
    } else {
      val aliases = transaction { Guild.findById(event.guild.idLong)?.settings?.aliases?.toList() }
      val alias = args[1]

      val message =
        when {
          // Checks that alias doesn't already exist
          aliases?.any { it.alias == alias } == true -> ":no_entry_sign: _Alias **`$alias`** already exists._"
          // Checks that alias isn't a command
          command == alias.toUpperCase() -> ":no_entry_sign: _Alias cannot be a command: **`$alias`**_"
          else -> {
            asyncTransaction {
              Guild.findById(event.guild.idLong)?.settings?.let {
                Alias.new {
                          name = command
                          this.alias = alias
                          settings = it
                }
              }
            }
          ":dancers: _New alias: **`$alias -> ${command.toLowerCase()}`**_"
          }
        }

      BotUtils.sendMessage(defaultChannel, message)
    }
  }

  override fun usage(prefix: String): String = "${prefix}alias [command name] [new command alias]"

  override fun description(): String = "Creates an alias, or alternate name, to a command for customization."
}
