package tech.gdragon.commands.settings

import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.option
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel

object SaveDestination {
  val command = Command(
    "save-destination",
    "Send recording to a specific channel, and, optionally, target a specific voice channel."
  ) {
    option<TextChannel>("destination", "Recordings will be sent to this channel.", required = true) {
      setChannelTypes(ChannelType.TEXT)
    }
    option<VoiceChannel>("channel", "Recordings started from this voice channel.") {
      setChannelTypes(ChannelType.VOICE, ChannelType.STAGE)
    }
  }
}
