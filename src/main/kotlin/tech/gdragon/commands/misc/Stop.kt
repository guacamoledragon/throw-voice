package tech.gdragon.commands.misc

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.i18n.Lang

class Stop : CommandHandler() {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.isEmpty()) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel

    val message =
      if (event.guild.audioManager.isConnected) {
        event.guild.audioManager.connectedChannel?.let {
          val save = BotUtils.autoSave(event.guild)
          BotUtils.leaveVoiceChannel(it, defaultChannel, save)
          ":wave: _Leaving **<#${it.id}>**_"
        } ?: ":no_entry_sign: _I am not in a channel_"

      } else {
        ":no_entry_sign: _I am not in a channel_"
      }

    BotUtils.sendMessage(defaultChannel, message)
  }

  override fun usage(prefix: String, lang: Lang): String = "${prefix}stop"

  override fun description(lang: Lang): String = "Ask the bot to stop recording and leave its current channel"
}
