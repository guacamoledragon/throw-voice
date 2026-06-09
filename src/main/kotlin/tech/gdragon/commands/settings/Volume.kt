package tech.gdragon.commands.settings

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.option
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import net.dv8tion.jda.api.entities.Guild as DiscordGuild
import tech.gdragon.i18n.Volume as VolumeTranslator

object Volume {
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
