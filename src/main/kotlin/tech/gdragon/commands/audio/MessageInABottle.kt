package tech.gdragon.commands.audio

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.commands.Command

class MessageInABottle : Command {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    BotUtils.sendMessage(event.channel, "`miab` has been deprecated, contact bot author if this functionality is desired.")
  }

  override fun usage(prefix: String): String = "${prefix}miab [seconds] [voice channel]"

  override fun description(): String = "Echos back the input number of seconds of the recording into the voice channel specified and then rejoins original channel (max 120 seconds)"
}
