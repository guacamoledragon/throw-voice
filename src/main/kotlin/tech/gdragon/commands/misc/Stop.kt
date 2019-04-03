package tech.gdragon.commands.misc

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand

class Stop : CommandHandler {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.isEmpty()) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel

    val message =
      if (event.guild.audioManager.isConnected) {
        val voiceChannel = event.guild.audioManager.connectedChannel

        BotUtils.leaveVoiceChannel(voiceChannel, defaultChannel)
        ":wave: _Leaving **<#${voiceChannel.id}>**_"
      } else {
        ":no_entry_sign: _I am not in a channel_"
      }

    BotUtils.sendMessage(defaultChannel, message)
  }

  override fun usage(prefix: String): String = "${prefix}stop"

  override fun description(): String = "Ask the bot to stop recording and leave its current channel"
}
