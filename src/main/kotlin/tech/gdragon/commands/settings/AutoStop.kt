package tech.gdragon.commands.settings

import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.option
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel

object AutoStop {
  val command = Command("autostop", "Set number of people in channel before leaving") {
    option<VoiceChannel>("channel", "The channel to autostop", true) {
      setChannelTypes(ChannelType.VOICE, ChannelType.STAGE)
    }
    option<Int>("threshold", "Number of people in channel before leaving the voice channel.") {
      setMinValue(0)
    }
  }
}
