package tech.gdragon.commands

import net.dv8tion.jda.core.entities.TextChannel
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

  val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
  val command = try {
    Command.valueOf(rawCommand.toUpperCase())
      .also {
        warnAboutDeprecation(prefix, defaultChannel, rawCommand)
      }
  } catch (e: IllegalArgumentException) {
    val aliases = transaction { Guild.findById(event.guild.idLong)?.settings?.aliases?.toList() }
    aliases
      ?.find { it.alias == rawCommand }
      ?.let { Command.valueOf(it.name) }
      ?.also {
        warnAboutDeprecation(prefix, defaultChannel, rawCommand)
      }
  }

  command?.handler?.action(args, event)
}

fun warnAboutDeprecation(prefix: String, channel: TextChannel, input: String) {
  if(Alias.deprecationMap.containsKey(input)) {
    BotUtils.sendMessage(
      channel,
      ":warning: _The command `$prefix$input` will become `$prefix${Alias.deprecationMap[input]}` in the next release. Use `${prefix}alias` to restore._")
  }
}
