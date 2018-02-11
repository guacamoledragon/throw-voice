package tech.gdragon.commands.audio

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.Command
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild
import tech.gdragon.listener.CombinedAudioRecorderHandler

class Save : Command {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.size in 0..1) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val message =
      if (event.guild.audioManager.connectedChannel == null) {
        ":no_entry_sign: _I am not currently recording._"
      } else {
        val voiceChannel = event.guild.audioManager.connectedChannel
        val audioReceiveHandler = event.guild.audioManager.receiveHandler as CombinedAudioRecorderHandler

        BotUtils.sendMessage(event.channel, ":floppy_disk: **Saving <#${voiceChannel.id}> recording...**")
        if (args.isEmpty()) {
          audioReceiveHandler.saveRecording(voiceChannel, event.channel)
          ""
        } else {
          val channelName = if (args.first().startsWith("#")) args.first().substring(1) else args.first()
          val channels = event.guild.getTextChannelsByName(channelName, true)

          if (channels.isEmpty()) {
            ":no_entry_sign: _Cannot find $channelName._"
          } else {
            channels.forEach { audioReceiveHandler.saveRecording(voiceChannel, it) }
            ""
          }
        }
      }

    if (message.isNotBlank())
      BotUtils.sendMessage(event.channel, message)
  }

  override fun usage(prefix: String): String = "${prefix}save | ${prefix}save [text channel output]"

  override fun description(): String = "Saves the current recording and outputs it to the current or specified text channel."
}
