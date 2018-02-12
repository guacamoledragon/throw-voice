package tech.gdragon.commands.misc

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.Command
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild
import tech.gdragon.listener.CombinedAudioRecorderHandler

class Join : Command {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.isEmpty()) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    transaction {
      val guild = Guild.findById(event.guild.idLong)

      val voiceChannel = event.member.voiceState.channel
      val message =
        if (voiceChannel == null) {
          ":no_entry_sign: _Please join a voice channel before using this command._"
        } else {
          val connectedChannel = event.guild.audioManager.connectedChannel
          if (connectedChannel != null && connectedChannel.members.contains(event.member)) {
            ":no_entry_sign: _I am already in **<#${connectedChannel.id}>**._"
          } else {

            if (event.guild.audioManager.isConnected) {
              guild?.settings?.let {
                if (it.autoSave) {
                  val audioReceiveHandler = event.guild.audioManager.receiveHandler as CombinedAudioRecorderHandler
                  audioReceiveHandler.saveRecording(connectedChannel, event.channel)
                  BotUtils.leaveVoiceChannel(connectedChannel)
                }
              }
            }

            BotUtils.joinVoiceChannel(voiceChannel, true)
            ":red_circle: **Recording on <#${voiceChannel.id}>**"
          }
        }

      BotUtils.sendMessage(event.channel, message)
    }
  }

  override fun usage(prefix: String): String = "${prefix}join"

  override fun description(): String = "Ask the bot to join and record in your current channel."
}
