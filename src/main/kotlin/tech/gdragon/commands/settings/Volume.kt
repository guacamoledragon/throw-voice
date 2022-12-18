package tech.gdragon.commands.settings

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang
import java.math.BigDecimal

class Volume : CommandHandler() {
  companion object {
    const val MAX_VOLUME = 100
  }

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent, pawa: Pawa) {
    require(args.size == 1) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val message: String =
      try {
        val volume = args.first().toInt()

        if (volume in 1..MAX_VOLUME) {
          val percentage = volume.toDouble() / MAX_VOLUME

          val settings = transaction {
            Guild[event.guild.idLong].settings
          }

          val translator = transaction {
            Guild[event.guild.idLong]
              .settings
              .language
              .let(Babel::volume)
          }

          settings.let {
            transaction {
              it.volume = BigDecimal.valueOf(percentage)
            }
            BotUtils.updateVolume(event.guild, percentage)
            ":loud_sound: _${translator.recording(volume.toString())}_"
          }
        } else {
          throw InvalidCommand(::usage, "Volume must be a number between 1-100: ${args.first()}")
        }
      } catch (e: NumberFormatException) {
        throw InvalidCommand(::usage, "Volume must be a positive number: ${args.first()}")
      }

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
    BotUtils.sendMessage(defaultChannel, message)
  }

  override fun usage(prefix: String, lang: Lang): String = Babel.volume(lang).usage(prefix)

  override fun description(lang: Lang): String = "Sets the percentage volume to record at, from 1-100%"
}
