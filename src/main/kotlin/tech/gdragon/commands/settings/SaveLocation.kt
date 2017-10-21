package tech.gdragon.commands.settings

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.Command
import tech.gdragon.db.dao.Guild

class SaveLocation : Command {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    transaction {
      val guild = Guild.findById(event.guild.idLong)
      val prefix = guild?.settings?.prefix ?: "!"

      require(args.size in 0..1) {
        BotUtils.sendMessage(event.channel, usage(prefix))
      }

      val message =
        guild?.settings?.let {
          if (args.isEmpty()) {
            it.defaultTextChannel = event.channel.idLong

            "Now defaulting to the ${event.channel.name} text channel."
          } else {
            val channelName = if(args.first().startsWith("#")) args.first().substring(1) else args.first()

            val channels = event.guild.getTextChannelsByName(channelName, true)

            if (channels.isEmpty()) {
              "Cannot find $channelName!"
            } else {
              it.defaultTextChannel = channels.first().idLong
              "Now defaulting to the ${channels.first().name} text channel."
            }
          }
        } ?: "Could not set default save location."

      BotUtils.sendMessage(event.channel, message)
    }
  }

  override fun usage(prefix: String): String = "${prefix}saveLocation [text channel name]"

  override fun description(): String = "Sets the text channel of message or the text channel specified as the default location to send files."
}
