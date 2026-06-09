package tech.gdragon.commands.settings

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.option
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.discord.message.ErrorEmbed
import tech.gdragon.i18n.AutoRecord as AutoRecordTranslator

object AutoRecord {
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
