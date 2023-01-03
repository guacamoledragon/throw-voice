package tech.gdragon.commands.settings

import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.option
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang
import tech.gdragon.i18n.AutoStop as AutoStopTranslator

class AutoStop : CommandHandler() {
  companion object {
    val command = Command("autostop", "Set number of people in channel before leaving") {
      option<VoiceChannel>("channel", "The channel to autostop", true) {
        setChannelTypes(ChannelType.VOICE, ChannelType.STAGE)
      }
      option<Int>("threshold", "Number of people in channel before leaving the voice channel.") {
        setMinValue(0)
      }
    }
  }

  override fun action(args: Array<String>, event: MessageReceivedEvent, pawa: Pawa) {
    require(args.size >= 2) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val message =
      try {
        val channelName = args.dropLast(1).joinToString(" ")
        val number: Int = when (args.last()) {
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
        } ?: 0

        val translator: AutoStopTranslator = pawa.translator(event.guild.idLong)

        if (channelName == "all") {
          val channels = event.guild.voiceChannels
          channels.forEach { pawa.autoStopChannel(it.idLong, it.name, event.guild.idLong, number.toLong()) }

          if (0 != number) {
            ":vibration_mode::wave: _${translator.all(number.toString())}_"
          } else {
            ":mobile_phone_off::wave: _${translator.none}_"
          }
        } else {
          val channels = event.guild.getVoiceChannelsByName(channelName, true)

          if (channels.isEmpty()) {
            "Cannot find voice channel `$channelName`."
          } else {
            channels.forEach { pawa.autoStopChannel(it.idLong, it.name, event.guild.idLong, number.toLong()) }
            val voiceChannel = channels.first()

            if (0 != number) {
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
