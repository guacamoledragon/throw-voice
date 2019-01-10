package tech.gdragon.commands.settings

import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.dao.Settings

class SaveLocation : CommandHandler {
  private fun setSaveLocation(settings: Settings, channel: TextChannel): String {
    return if (channel.canTalk()) {
      settings.defaultTextChannel = channel.idLong
      ":file_folder: _All messages will default to channel **${channel.asMention}**._"
    } else {
      ":no_entry_sign: _Cannot send messages in **${channel.asMention}**, please configure permissions and try again._"
    }
  }

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.size in 0..1) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    transaction {
      val guild = Guild.findById(event.guild.idLong)

      val message =
        guild?.settings?.let {
          if (args.isEmpty()) {
            setSaveLocation(it, event.channel)
          } else {
            val channelName = if (args.first().startsWith("#")) args.first().substring(1) else args.first()
            val channels = event.guild.getTextChannelsByName(channelName, true)

            if (channels.isEmpty()) {
              ":no_entry_sign: _Cannot find text channel **${args.first()}**!_"
            } else {
              setSaveLocation(it, channels.first())
            }
          }
        } ?: ":no_entry_sign: _Could not set default save location._"

      BotUtils.sendMessage(event.channel, message)
    }
  }

  override fun usage(prefix: String): String = "${prefix}saveLocation | ${prefix}saveLocation [text channel name]"

  override fun description(): String = "Set default channel to send messages in, including the link to the voice recording"
}
