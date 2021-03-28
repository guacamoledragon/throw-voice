package tech.gdragon.commands

import mu.KotlinLogging
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import tech.gdragon.db.dao.Guild
import tech.gdragon.discord.Command
import tech.gdragon.metrics.EventTracer

abstract class CommandHandler : KoinComponent {
  protected val logger = KotlinLogging.logger {}

  val tracer: EventTracer = getKoin().get()

  @Throws(InvalidCommand::class)
  abstract fun action(args: Array<String>, event: GuildMessageReceivedEvent)

  abstract fun usage(prefix: String): String
  abstract fun description(): String
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
  }

  command?.handler?.let {
    it.tracer.sendEvent(mapOf("command" to command.name))
    it.action(args, event)
  }
}
