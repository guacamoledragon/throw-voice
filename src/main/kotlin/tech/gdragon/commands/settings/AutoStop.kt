package tech.gdragon.commands.settings

import mu.withLoggingContext
import net.dv8tion.jda.api.entities.GuildChannel
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.asyncTransaction
import tech.gdragon.db.dao.Channel
import tech.gdragon.db.dao.Guild
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang

class AutoStop : CommandHandler() {

  private fun updateChannelAutoStop(channel: GuildChannel, autoStop: Int?) {
    withLoggingContext("guild" to channel.guild.name, "text-channel" to channel.name) {
      channel.guild.run {
        transaction {
          Guild.findOrCreate(idLong, name, region.name)
        }
      }.let { guild ->
        asyncTransaction {
          Channel
            .findOrCreate(channel.idLong, channel.name, guild)
            .also { it.autoStop = autoStop }
        }
      }
    }
  }

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.size >= 2) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val message =
      try {
        val channelName = args.dropLast(1).joinToString(" ")
        val number: Int? = when (args.last()) {
          "off" -> null
          "0" -> null
          else -> {
            val lastArg = args.last().toInt()

            if (lastArg < 0) {
              throw InvalidCommand(::usage, "Number must be positive: $lastArg")
            } else {
              lastArg
            }
          }
        }

        val translator = transaction { Guild[event.guild.idLong].settings.language.let(Babel::autostop) }

        if (channelName == "all") {
          val channels = event.guild.voiceChannels
          channels.forEach { updateChannelAutoStop(it, number) }

          if (number != null) {
            ":vibration_mode::wave: _${translator.all(number.toString())}_"
          } else {
            ":mobile_phone_off::wave: _${translator.none}_"
          }
        } else {
          val channels = event.guild.getVoiceChannelsByName(channelName, true)

          if (channels.isEmpty()) {
            "Cannot find voice channel `$channelName`."
          } else {
            channels.forEach { updateChannelAutoStop(it, number) }
            val voiceChannel = channels.first()

            if (number != null) {
              ":vibration_mode::wave: _${translator.one(voiceChannel.id, number.toString())}_"
            } else {
              ":mobile_phone_off::wave: _${translator.some(voiceChannel.id)}_"
            }
          }
        }
      } catch (e: NumberFormatException) {
        throw InvalidCommand(::usage, "Could not parse number argument: ${e.message}")
      } catch (e: IllegalArgumentException) {
        throw InvalidCommand(::usage, "Number must be positive: ${e.message}")
      }

    BotUtils.sendMessage(event.channel, message)
  }

  override fun usage(prefix: String, lang: Lang): String = Babel.autostop(lang).usage(prefix)

  override fun description(lang: Lang): String =
    "Sets the number of players for the bot to autostop a voice channel. All will apply number to all voice channels."
}
