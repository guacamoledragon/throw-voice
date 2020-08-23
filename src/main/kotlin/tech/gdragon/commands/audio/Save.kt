package tech.gdragon.commands.audio

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.listener.CombinedAudioRecorderHandler

class Save : CommandHandler() {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.size in 0..1) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
    val message =
      if (event.guild.audioManager.connectedChannel == null) {
        ":no_entry_sign: _I am not currently recording._"
      } else {
        val voiceChannel = event.guild.audioManager.connectedChannel
        val save = true

        BotUtils.sendMessage(defaultChannel, ":floppy_disk: **Saving <#${voiceChannel?.id}>'s recording...**")
        if (args.isEmpty()) {
          BotUtils.leaveVoiceChannel(voiceChannel!!, defaultChannel, save)
          ""
        } else {
          val channelName = if (args.first().startsWith("#")) args.first().substring(1) else args.first()
          val channels = event.guild.getTextChannelsByName(channelName, true)

          if (channels.isEmpty()) {
            ":no_entry_sign: _Cannot find $channelName._"
          } else {
            channels.forEach {
              BotUtils.leaveVoiceChannel(voiceChannel!!, it, save)
            }
            ""
          }
        }
      }

    if (message.isNotBlank())
      BotUtils.sendMessage(defaultChannel, message)
  }

  override fun usage(prefix: String): String = "${prefix}save | ${prefix}save [text channel output]"

  override fun description(): String = "Saves the current recording and outputs it to the current or specified text channel."
}
