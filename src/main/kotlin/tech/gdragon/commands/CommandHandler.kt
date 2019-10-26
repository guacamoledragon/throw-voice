package tech.gdragon.commands

import com.codahale.metrics.Gauge
import com.codahale.metrics.MetricRegistry
import com.github.rollingmetrics.counter.ResetPeriodicallyCounter
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.KoinComponent
import org.koin.core.inject
import tech.gdragon.db.dao.Guild
import tech.gdragon.discord.Command
import tech.gdragon.metrics.Metrics
import java.time.Duration

abstract class CommandHandler : KoinComponent {
  private val metrics: Metrics by inject()
  protected val usageCounter = ResetPeriodicallyCounter(Duration.ofDays(1))

  init {
    val gauge = Gauge<Long> { usageCounter.sum }
    metrics.registry.register(MetricRegistry.name(this::class.java), gauge)
  }

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

  command?.handler?.action(args, event)
}
