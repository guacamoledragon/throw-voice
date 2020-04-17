package tech.gdragon.commands.misc

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand

class Stop : CommandHandler() {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.isEmpty()) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel

    val message =
      if (event.guild.audioManager.isConnected) {
        event.guild.audioManager.connectedChannel?.let {
          BotUtils.leaveVoiceChannel(it, defaultChannel)
          ":wave: _Leaving **<#${it.id}>**_"
        } ?: ":no_entry_sign: _I am not in a channel_"

      } else {
        ":no_entry_sign: _I am not in a channel_"
      }

    BotUtils.sendMessage(defaultChannel, message)
  }

  override fun usage(prefix: String): String = "${prefix}stop"

  override fun description(): String = "Ask the bot to stop recording and leave its current channel"
}
