package tech.gdragon.commands.misc

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild
import tech.gdragon.listener.CombinedAudioRecorderHandler

class Join : CommandHandler {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.isEmpty()) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
    val voiceChannel = event.member.voiceState.channel
    val message: String? =
      if (voiceChannel == null) {
        ":no_entry_sign: _Please join a voice channel before using this command._"
      } else {
        val connectedChannel = event.guild.audioManager.connectedChannel
        if (connectedChannel != null && connectedChannel.members.contains(event.member)) {
          ":no_entry_sign: _I am already in **<#${connectedChannel.id}>**._"
        } else {
          // This is where the happy path logic begins
          if (event.guild.audioManager.isConnected) {
            transaction {
              val guild = Guild.findById(event.guild.idLong)

              guild?.settings?.let {
                if (it.autoSave) {
                  val audioReceiveHandler = event.guild.audioManager.receiveHandler as CombinedAudioRecorderHandler
                  audioReceiveHandler.saveRecording(connectedChannel, defaultChannel)
                  BotUtils.leaveVoiceChannel(connectedChannel)
                }
              }
            }
          }

          // We need to give something to the onError handler because sometimes life doesn't do what we want
          BotUtils.joinVoiceChannel(voiceChannel, defaultChannel) { ex ->
            val errorMessage = ":no_entry_sign: _Cannot join **<#${defaultChannel.id}>**, need permission:_ ```${ex.permission}```"
            BotUtils.sendMessage(defaultChannel, errorMessage)
          }

          null // TODO: This is weird, but the problem is probably with the way the logic is structured
        }
      }

    message?.let {
      BotUtils.sendMessage(defaultChannel, it)
    }
  }

  override fun usage(prefix: String): String = "${prefix}join"

  override fun description(): String = "Ask the bot to join and record in your current channel."
}
