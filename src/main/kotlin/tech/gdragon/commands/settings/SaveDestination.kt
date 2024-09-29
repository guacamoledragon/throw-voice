package tech.gdragon.commands.settings

import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.components.getOption
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.i18n.SaveLocation

object SaveDestination {
  val command = Command(
    "save-destination",
    "Send recording to a specific channel, and, optionally, target a specific voice channel."
  ) {
    restrict(guild = true)
    option<TextChannel>("destination", "Recordings will be sent to this channel.", required = true) {
      setChannelTypes(ChannelType.TEXT)
    }
    option<VoiceChannel>("channel", "Recordings started from this voice channel.") {
      setChannelTypes(ChannelType.VOICE, ChannelType.STAGE)
    }
    option<Boolean>("disable", "Revert to default behaviour for all if no `channel` specified.")
  }

  fun slashHandler(pawa: Pawa): suspend CoroutineEventListener.(GenericCommandInteractionEvent) -> Unit = { event ->
    val destinationChannel = event.getOption<TextChannel>("destination")!!
    val voiceChannel = event.getOption<VoiceChannel>("channel")
    val disable = event.getOption<Boolean>("disable") ?: false

    val message =
      event.guild?.let { guild ->
        val translator: SaveLocation = pawa.translator(guild.idLong)

        if (disable) {
          pawa.saveDestination(guild.idLong, null)
          ":file_folder: _${translator.current}_"
        } else {
          pawa.saveDestination(guild.idLong, destinationChannel.idLong)
          ":file_folder: _${translator.channel(destinationChannel.asMention)}_"
        }
      }

    println("destinationChannel = ${destinationChannel}")
    println("voiceChannel = ${voiceChannel}")
    println("disable = ${disable}")

    event.reply_("$message").queue()
  }
}
