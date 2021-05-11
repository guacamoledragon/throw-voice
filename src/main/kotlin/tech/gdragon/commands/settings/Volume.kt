package tech.gdragon.commands.settings

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.i18n.Lang
import java.math.BigDecimal

class Volume : CommandHandler() {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.size == 1) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val message: String =
      try {
        val volume = args.first().toInt()

        if (volume in 1..100) {
          val percentage = volume.toDouble() / 100f

          val settings = transaction {
            Guild.findById(event.guild.idLong)?.settings
          }

          settings?.let {
            transaction {
              it.volume = BigDecimal.valueOf(percentage)
            }
            BotUtils.updateVolume(event.guild, percentage)
            ":loud_sound: _Recording at **$volume%** volume._"
          } ?: ":no_entry_sign: _Could not set recording volume._"
        } else {
          throw InvalidCommand(::usage, "Volume must be a number between 1-100: ${args.first()}")
        }
      } catch (e: NumberFormatException) {
        throw InvalidCommand(::usage, "Volume must be a positive number: ${args.first()}")
      }

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
    BotUtils.sendMessage(defaultChannel, message)
  }

  override fun usage(prefix: String, lang: Lang): String = "${prefix}volume [1-100]"

  override fun description(lang: Lang): String = "Sets the percentage volume to record at, from 1-100%"
}
