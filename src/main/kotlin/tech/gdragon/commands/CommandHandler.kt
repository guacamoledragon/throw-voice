package tech.gdragon.commands

import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent.getKoin
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.db.dao.Guild
import tech.gdragon.discord.Command
import tech.gdragon.i18n.Lang
import tech.gdragon.metrics.EventTracer

abstract class CommandHandler {
  companion object {
    val tracer: EventTracer by lazy {
      getKoin().get()
    }
  }

  protected val logger = KotlinLogging.logger {}

  val tracer: EventTracer by lazy {
    getKoin().get()
  }

  @Throws(InvalidCommand::class)
  abstract fun action(args: Array<String>, event: MessageReceivedEvent, pawa: Pawa)

  abstract fun usage(prefix: String, lang: Lang = Lang.EN): String
  abstract fun description(lang: Lang = Lang.EN): String
}

data class InvalidCommand(val usage: (String) -> String, val reason: String) : Throwable()

@Throws(InvalidCommand::class)
fun handleCommand(parentSpan: Span, event: MessageReceivedEvent, pawa: Pawa, prefix: String, rawInput: String) {
  val tokens = rawInput.substring(prefix.length).split(" ")
  val rawCommand = tokens.first()
  val args = tokens.drop(1).toTypedArray()

  val command = try {
    Command.valueOf(rawCommand.uppercase())
  } catch (_: IllegalArgumentException) {

    val alias = transaction {
      Guild.findById(event.guild.idLong)
        ?.settings
        ?.aliases
        ?.find { it.alias == rawCommand }
        ?.name
    }

    if (alias == null) {
      val tracer: EventTracer = getKoin().get()
      tracer.sendEvent(mapOf("command-not-found" to rawCommand.uppercase()))
    }

    alias?.let(Command::valueOf)
  }

  command?.handler?.let {

    it.tracer.sendEvent(mapOf("command" to command.name))
    parentSpan.setAttribute("command", command.name.lowercase())

    val span = getKoin().get<Tracer>()
      .spanBuilder("${command.name} Command")
      .setParent(Context.current().with(parentSpan))
      .startSpan()
    span.makeCurrent().use { _ ->
      it.action(args, event, pawa)
    }
    span.end()
  }
}
