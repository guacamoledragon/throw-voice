package tech.gdragon.commands.audio

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.commands.Command

class Echo: Command {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    BotUtils.sendMessage(event.channel, "`echo` has been deprecated, contact bot author if this functionality is desired.")
  }

  override fun usage(prefix: String): String = "${prefix}echo [seconds]"

  override fun description(): String = "Echos back the input number of seconds of the recording into the voice channel (max 120 seconds)"
}
