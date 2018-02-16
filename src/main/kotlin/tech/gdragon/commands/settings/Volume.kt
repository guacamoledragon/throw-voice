package tech.gdragon.commands.settings

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.Command
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild
import java.math.BigDecimal

class Volume : Command {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.size == 1) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    transaction {
      val guild = Guild.findById(event.guild.idLong)
      val prefix = guild?.settings?.prefix ?: "!"

      val message: String =
        try {
          val volume = args.first().toInt()

          if (volume in 1..100) {
            val percentage = volume.toDouble() / 100f

            guild?.settings?.let {
              it.volume = BigDecimal.valueOf(percentage)
              "Volume set to $volume% for next recording!"
            } ?: "Volume could not be set to $volume%."
          } else {
            usage(prefix)
          }
        } catch (e: NumberFormatException) {
          usage(prefix)
        }

      BotUtils.sendMessage(event.channel, message)
    }
  }

  override fun usage(prefix: String): String = "${prefix}volume [1-100]"

  override fun description(): String = "Sets the percentage volume to record at, from 1-100%"
}
