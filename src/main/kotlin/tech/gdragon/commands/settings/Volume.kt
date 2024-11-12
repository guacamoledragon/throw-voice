package tech.gdragon.commands.settings

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.option
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang
import net.dv8tion.jda.api.entities.Guild as DiscordGuild
import tech.gdragon.i18n.Volume as VolumeTranslator

class Volume : CommandHandler() {
  companion object {
    const val MAX_VOLUME = 100L

    val command = Command("volume", "Set the recording volume.") {
      option<Long>("volume", "The recording volume in percent from 1-100.", required = true) {
        setMinValue(1)
        setMaxValue(MAX_VOLUME)
      }
    }

    fun handler(pawa: Pawa, guild: DiscordGuild, volume: Long): String {
      val translator: VolumeTranslator = pawa.translator(guild.idLong)
      val percentage = volume.toDouble() / MAX_VOLUME
      pawa.volume(guild.idLong, percentage)
      BotUtils.updateVolume(guild, percentage)

      return ":loud_sound: _${translator.recording(volume.toString())}_"
    }

    fun slashHandler(pawa: Pawa): suspend CoroutineEventListener.(GenericCommandInteractionEvent) -> Unit = { event ->
      event.guild?.let {
        val volume = event.getOption("volume")?.asLong!!
        val message = handler(pawa, it, volume)
        event.reply(message).await()
      }
    }
  }

  override fun action(args: Array<String>, event: MessageReceivedEvent, pawa: Pawa) {
    require(args.size == 1) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val volume = try {
      args.first().toLong()
    } catch (e: NumberFormatException) {
      throw InvalidCommand(::usage, "Volume must be a valid number: ${args.first()}")
    }

    require(volume in 1..MAX_VOLUME) {
      throw InvalidCommand(::usage, "Volume must be a number between 1-100: ${args.first()}")
    }

    val message = handler(pawa, event.guild, volume)
    BotUtils.sendMessage(event.channel, message)
  }

  override fun usage(prefix: String, lang: Lang): String = Babel.volume(lang).usage(prefix)

  override fun description(lang: Lang): String = "Sets the percentage volume to record at, from 1-100%"
}
