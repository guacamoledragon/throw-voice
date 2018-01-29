package tech.gdragon.commands.audio

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.Command
import tech.gdragon.db.dao.Guild
import tech.gdragon.listener.CombinedAudioRecorderHandler

class Clip : Command {
  override fun action(args: Array<out String>, event: GuildMessageReceivedEvent) {
    val prefix = transaction {
      val guild = Guild.findById(event.guild.idLong)
      guild?.settings?.prefix ?: "!"
    }

    require(args.size in 1..2) {
      BotUtils.sendMessage(event.channel, usage(prefix))
    }

    val message =
      if (event.guild.audioManager.connectedChannel == null) {
        "I wasn't recording!"
      } else {
        val voiceChannel = event.guild.audioManager.connectedChannel
        val audioReceiveHandler = event.guild.audioManager.receiveHandler as CombinedAudioRecorderHandler

        try {
          val seconds = args.first().toLong()

          if (args.size == 1) {
            audioReceiveHandler.saveClip(seconds, voiceChannel, event.channel)
            ""
          } else {
            val channelName = if (args[1].startsWith("#")) args[1].substring(1) else args[1]
            val channels = event.guild.getTextChannelsByName(channelName, true)

            if (channels.isEmpty()) {
              "Cannot find $channelName."
            } else {
              channels.forEach { audioReceiveHandler.saveClip(seconds, voiceChannel, event.channel) }
              ""
            }
          }
        } catch (e: NumberFormatException) {
          usage(prefix)
        }
      }

    if (message.isNotBlank())
      BotUtils.sendMessage(event.channel, message)
  }

  override fun usage(prefix: String): String = "${prefix}clip [seconds] | ${prefix}clip [seconds] [text channel output]"

  override fun description(): String = "Saves a clip of the specified length (seconds) and outputs it in the current or specified text channel."
}
