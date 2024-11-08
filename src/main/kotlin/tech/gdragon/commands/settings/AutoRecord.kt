package tech.gdragon.commands.settings

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.option
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.discord.message.ErrorEmbed
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang
import tech.gdragon.i18n.AutoRecord as AutoRecordTranslator

class AutoRecord : CommandHandler() {
  companion object {
    val command = Command("autorecord", "Set number of people in channel before recording") {
      option<AudioChannel>("channel", "The channel to autorecord", true) {
        setChannelTypes(ChannelType.VOICE, ChannelType.STAGE)
      }
      option<Int>("threshold", "Number of people in channel before recording.", true) {
        setMinValue(0)
      }
    }

    fun slashHandler(pawa: Pawa): suspend CoroutineEventListener.(GenericCommandInteractionEvent) -> Unit = { event ->
      val audioChannel = event.getOption("channel")!!.asChannel.asAudioChannel()
      val threshold = event.getOption("threshold")!!.asInt

      event.guild?.let {
        if (pawa.isStandalone) {
          event.reply(handler(pawa, it.idLong, listOf(audioChannel), threshold)).await()
        } else {
          val errorEmbed = ErrorEmbed(
            "You cannot use /autorecord command in this server.",
            "This command is only available for PawaLite, visit support server for more information."
          )
          event.reply(errorEmbed.message).await()
        }
      }
    }

    private fun handler(pawa: Pawa, guildId: Long, channels: List<AudioChannel>, threshold: Int): String {
      val translator: AutoRecordTranslator = pawa.translator(guildId)

      val message = when {
        channels.isEmpty() -> ":no_entry_sign: _${translator.notFound}_"
        1 == channels.size -> {
          val audioChannel = channels.first()
          pawa.autoRecordChannel(audioChannel.idLong, audioChannel.name, audioChannel.guild.idLong, threshold)

          if (0 != threshold) {
            ":vibration_mode::red_circle: _${translator.one(audioChannel.id, threshold.toString())}_"
          } else {
            ":mobile_phone_off::red_circle: _${translator.some(audioChannel.id)}_"
          }
        }

        else -> {
          channels.forEach {
            pawa.autoRecordChannel(it.idLong, it.name, it.guild.idLong, threshold)
          }

          if (0 != threshold) {
            ":vibration_mode::red_circle: _${translator.all(threshold.toString())}_"
          } else {
            ":mobile_phone_off::red_circle: _${translator.none}_"
          }
        }
      }

      // TODO: Minor optimization, delete rows that have the defaults,

      return message
    }
  }

  /**
   * Sets the autoRecord value for a given voice channel. `null` represents autoRecord for that
   * channel is disabled.
   */
  override fun action(args: Array<String>, event: MessageReceivedEvent, pawa: Pawa) {
    require(pawa.isStandalone) {
      BotUtils.sendMessage(
        event.channel,
        ":no_entry_sign: _Command is currently disabled, please see https://pawa.im/#/commands for more information._"
      )
      return
    }
    require(args.size >= 2) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
    val message: String =
      try {
        val channelName = args.dropLast(1).joinToString(" ")
        val number: Int = when (args.last()) {
          "off" -> null
          "0" -> null
          else -> {
            val lastArg = args.last().toInt()

            if (lastArg < 0)
              throw InvalidCommand(::usage, "Number must be positive: $lastArg")
            else
              lastArg
          }
        } ?: 0

        val channels: List<AudioChannel> = if ("all" == channelName) {
          event.guild.stageChannels + event.guild.voiceChannels
        } else {
          event.guild.getVoiceChannelsByName(channelName, true) + event.guild.getStageChannelsByName(channelName, true)
        }

        handler(pawa, event.guild.idLong, channels, number)

      } catch (e: NumberFormatException) {
        throw InvalidCommand(::usage, "Could not parse number argument: ${e.message}")
      }

    BotUtils.sendMessage(defaultChannel, message)
  }

  override fun usage(prefix: String, lang: Lang): String = Babel.autorecord(lang).usage(prefix)

  override fun description(lang: Lang): String = Babel.autorecord(lang).description
}
