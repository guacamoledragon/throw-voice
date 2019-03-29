package tech.gdragon.commands.misc

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild
import tech.gdragon.listener.CombinedAudioRecorderHandler

class Stop : CommandHandler {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.isEmpty()) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    transaction {
      val guild = Guild.findById(event.guild.idLong)
      val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel

      val message =
        if (event.guild.audioManager.isConnected) {
          val voiceChannel = event.guild.audioManager.connectedChannel

          guild?.settings?.let {
            if (it.autoSave) {
              val audioReceiveHandler = event.guild.audioManager.receiveHandler as CombinedAudioRecorderHandler
              audioReceiveHandler.saveRecording(voiceChannel, defaultChannel)
            }
          }

          BotUtils.leaveVoiceChannel(voiceChannel)
          ":wave: _Leaving **<#${voiceChannel.id}>**_"
        } else {
          ":no_entry_sign: _I am not in a channel_"
        }

      BotUtils.sendMessage(defaultChannel, message)
    }
  }

  override fun usage(prefix: String): String = "${prefix}leave"

  override fun description(): String = "Ask the bot to stop recording and leave its current channel"
}
