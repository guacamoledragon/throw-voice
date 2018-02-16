package tech.gdragon.commands

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.db.dao.Alias
import tech.gdragon.db.dao.Guild
import java.util.*

interface Command {
  @Throws(InvalidCommand::class)
  fun action(args: Array<String>, event: GuildMessageReceivedEvent)
  fun usage(prefix: String): String
  fun description(): String
}

data class InvalidCommand(val usage: (String) -> String, val reason: String) : Throwable()

object CommandHandler {
  @JvmField
  val parser = CommandParser()
  var commands = HashMap<String, Command>()

  @JvmStatic
  @Throws(InvalidCommand::class)
  fun handleCommand(event: GuildMessageReceivedEvent, commandContainer: CommandContainer): Boolean {
    return transaction {
      var isSuccess = false
      val settings = Guild.findById(event.guild.idLong)?.settings
      val command = commandContainer.command

      if (!commands.containsKey(command)) {
        for (alias in (settings?.aliases ?: emptyList<Alias>())) {
          if (alias.alias == command) {
            commands[alias.name]?.action(commandContainer.args, event)
            isSuccess = true
            break
          }
        }
      } else {
        commands[command]?.action(commandContainer.args, event)
        isSuccess = true
      }

      isSuccess
    }
  }
}

class CommandParser {
  fun parse(prefix: String, raw: String): CommandContainer {
    val tokens = raw.substring(prefix.length).split(" ")

    val command = tokens.first()
    val args: Array<String> = tokens.drop(1).toTypedArray()

    return CommandContainer(prefix, command, args)
  }
}

data class CommandContainer(val prefix: String, val command: String, val args: Array<String>) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as CommandContainer

    if (!Arrays.equals(args, other.args)) return false

    return true
  }

  override fun hashCode(): Int {
    return Arrays.hashCode(args)
  }
}
