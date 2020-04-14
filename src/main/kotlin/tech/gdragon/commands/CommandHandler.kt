package tech.gdragon.commands

import com.github.rollingmetrics.counter.ResetPeriodicallyCounter
import mu.KotlinLogging
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.KoinComponent
import tech.gdragon.db.dao.Guild
import tech.gdragon.discord.Command
import java.time.Duration

abstract class CommandHandler : KoinComponent {
  protected val usageCounter = ResetPeriodicallyCounter(Duration.ofDays(1))
  protected val logger = KotlinLogging.logger {}

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
