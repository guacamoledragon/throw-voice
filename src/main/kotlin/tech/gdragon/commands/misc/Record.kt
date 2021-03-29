package tech.gdragon.commands.misc

import mu.withLoggingContext
import net.dv8tion.jda.api.entities.VoiceChannel
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import java.lang.IllegalArgumentException

class Record : CommandHandler() {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(standalone || args.isEmpty()) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
    val voiceChannel: VoiceChannel? = if (standalone) {
      logger.info { args.joinToString(separator = " ") }
      event.jda.getVoiceChannelsByName(args.joinToString(separator = " "), false)
        .firstOrNull()
        ?: event.member?.voiceState?.channel
    } else {
      event.member?.voiceState?.channel
    }
    val message: String? =
      if (voiceChannel == null) {
        ":no_entry_sign: _Please join a voice channel before using this command._"
      } else {
        val connectedChannel = event.guild.audioManager.connectedChannel
        if (connectedChannel != null && connectedChannel.members.contains(event.member)) {
          ":no_entry_sign: _I am already in **<#${connectedChannel.id}>**._"
        } else {
          // This is where the happy path logic begins

          // Leave a previous voice channel
          if (event.guild.audioManager.isConnected) {
            connectedChannel?.let {
              val save = BotUtils.autoSave(event.guild)
              BotUtils.leaveVoiceChannel(it, defaultChannel, save)
            }
          }

          // We need to give something to the onError handler because sometimes life doesn't do what we want
          withLoggingContext("guild" to event.guild.name, "voice-channel" to voiceChannel.name) {
            try {
              BotUtils.recordVoiceChannel(voiceChannel, defaultChannel) { ex ->
                val errorMessage = ":no_entry_sign: _Cannot record on **<#${voiceChannel.id}>**, need permission:_ ```${ex.permission}```"
                BotUtils.sendMessage(defaultChannel, errorMessage)
              }
            } catch (e: IllegalArgumentException) {
              logger.warn(e::message)
            }
          }
          null // TODO: This is weird, but the problem is probably with the way the logic is structured
        }
      }

    message?.let {
      BotUtils.sendMessage(defaultChannel, it)
    }
  }

  override fun usage(prefix: String): String = "${prefix}record"

  override fun description(): String = "Ask the bot to join and record in your current channel."
}
