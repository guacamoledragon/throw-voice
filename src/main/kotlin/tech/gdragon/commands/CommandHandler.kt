package tech.gdragon.commands

import io.opentelemetry.api.trace.Span
import mu.KotlinLogging
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.java.KoinJavaComponent
import tech.gdragon.db.dao.Guild
import tech.gdragon.discord.Command
import tech.gdragon.i18n.Lang
import tech.gdragon.metrics.EventTracer

abstract class CommandHandler : KoinComponent {
  protected val logger = KotlinLogging.logger {}
  protected val standalone = getKoin().getProperty<String>("BOT_STANDALONE").toBoolean()

  val tracer: EventTracer = getKoin().get()

  @Throws(InvalidCommand::class)
  abstract fun action(args: Array<String>, event: GuildMessageReceivedEvent)

  abstract fun usage(prefix: String, lang: Lang = Lang.EN): String
  abstract fun description(lang: Lang = Lang.EN): String
}

data class InvalidCommand(val usage: (String) -> String, val reason: String) : Throwable()

@Throws(InvalidCommand::class)
fun handleCommand(event: GuildMessageReceivedEvent, prefix: String, rawInput: String) {
  val tokens = rawInput.substring(prefix.length).split(" ")
  val rawCommand = tokens.first()
  val args = tokens.drop(1).toTypedArray()

  val command = try {
    Command.valueOf(rawCommand.uppercase())
  } catch (e: IllegalArgumentException) {

    val alias = transaction {
      Guild.findById(event.guild.idLong)
        ?.settings
        ?.aliases
        ?.find { it.alias == rawCommand }
        ?.name
    }

    if (alias == null) {
      val tracer: EventTracer = KoinJavaComponent.getKoin().get()
      tracer.sendEvent(mapOf("command-not-found" to rawCommand.uppercase()))
    }

    alias?.let(Command::valueOf)
  }

  command?.handler?.let {
    it.tracer.sendEvent(mapOf("command" to command.name))
    Span.current().setAttribute("command", command.name)
    it.action(args, event)
  }
}
