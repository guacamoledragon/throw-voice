package tech.gdragon.commands.misc

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.DiscordBot
import tech.gdragon.commands.Command
import tech.gdragon.db.dao.Guild

class Leave : Command {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    transaction {
      val guild = Guild.findById(event.guild.idLong)
      val prefix = guild?.settings?.prefix ?: "!"

      require(args.isEmpty()) {
        BotUtils.sendMessage(event.channel, usage(prefix))
      }

      val message =
        if (event.guild.audioManager.isConnected) {
          guild?.settings?.let {
            if (it.autoSave)
              DiscordBot.writeToFile(event.guild)
          }

          val voiceChannel = event.guild.audioManager.connectedChannel
          DiscordBot.leaveVoiceChannel(voiceChannel)
          "Leaving ${voiceChannel.name}"
        } else {
          "I am not in a channel!"
        }

      BotUtils.sendMessage(event.channel, message)
    }
  }

  override fun usage(prefix: String): String = "${prefix}leave"

  override fun description(): String = "Force the bot to leave it's current channel"
}
