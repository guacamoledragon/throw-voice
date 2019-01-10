package tech.gdragon.commands

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.settings.Alias
import tech.gdragon.db.dao.Guild
import tech.gdragon.discord.Command

interface CommandHandler {
  @Throws(InvalidCommand::class)
  fun action(args: Array<String>, event: GuildMessageReceivedEvent)

  fun usage(prefix: String): String
  fun description(): String
}

data class InvalidCommand(val usage: (String) -> String, val reason: String) : Throwable()

@Throws(InvalidCommand::class)
fun handleCommand(event: GuildMessageReceivedEvent, prefix: String, rawInput: String) {
  val tokens = rawInput.substring(prefix.length).split(" ")
  val rawCommand = tokens.first()
  val args = tokens.drop(1).toTypedArray()

  val command = try {
    Command.valueOf(rawCommand.toUpperCase())
  } catch (e: IllegalArgumentException) {
    val aliases = transaction { Guild.findById(event.guild.idLong)?.settings?.aliases?.toList() }
    aliases
      ?.find { it.alias == rawCommand }
      ?.let { Command.valueOf(it.name) }
      ?.also {
        if(Alias.deprecationMap.containsKey(rawCommand)) {
          BotUtils.sendMessage(
            event.channel,
            ":warning: _The command `$prefix$rawCommand` will become `$prefix${Alias.deprecationMap[rawCommand]}` in the next release. Use `${prefix}alias` to restore._")
        }
      }
  }

  command?.handler?.action(args, event)
}
