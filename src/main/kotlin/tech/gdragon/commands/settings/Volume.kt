package tech.gdragon.commands.settings

import dev.minn.jda.ktx.interactions.Command
import dev.minn.jda.ktx.interactions.option
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
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
    const val MAX_VOLUME = 100

    val command = Command("volume", "Set the recording volume.") {
      option<Int>("volume", "The recording volume in percent from 1-100.") {
        setMinValue(1)
        setMaxValue(MAX_VOLUME.toLong())
      }
    }

    fun handler(pawa: Pawa, guild: DiscordGuild, volume: Int): String {
      val translator: VolumeTranslator = pawa.translator(guild.idLong)
      val percentage = volume.toDouble() / MAX_VOLUME
      pawa.volume(guild.idLong, percentage)
      BotUtils.updateVolume(guild, percentage)

      return ":loud_sound: _${translator.recording(volume.toString())}_"
    }
  }

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent, pawa: Pawa) {
    require(args.size == 1) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val volume = try {
      args.first().toInt()
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
