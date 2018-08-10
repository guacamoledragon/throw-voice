package tech.gdragon.commands.settings

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.Command
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild

class SaveLocation : Command {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.size in 0..1) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    transaction {
      val guild = Guild.findById(event.guild.idLong)

      val message =
        guild?.settings?.let {
          if (args.isEmpty()) {
            if (event.channel.canTalk()) {
              it.defaultTextChannel = event.channel.idLong
              ":file_folder: _All messages will default to channel **<#${event.channel.id}>**._"
            } else {
              ":no_entry_sign: _Cannot send messages in **<#${event.channel.id}>**, please configure permissions and try again._"
            }
          } else {
            val channelName = if (args.first().startsWith("#")) args.first().substring(1) else args.first()
            val channels = event.guild.getTextChannelsByName(channelName, true)

            if (channels.isEmpty()) {
              ":no_entry_sign: _Cannot find text channel **${args.first()}**!_"
            } else {
              val channel = channels.first()
              if (channel.canTalk()) {
                it.defaultTextChannel = channel.idLong
                ":file_folder: _All messages will default to channel **<#${channel.id}>**._"
              } else {
                ":no_entry_sign: _Cannot send messages in **<#${channel.id}>**, please configure permissions and try again._"
              }
            }
          }
        } ?: ":no_entry_sign: _Could not set default save location._"

      BotUtils.sendMessage(event.channel, message)
    }
  }

  override fun usage(prefix: String): String = "${prefix}saveLocation | ${prefix}saveLocation [text channel name]"

  override fun description(): String = "Set default channel to send messages in, including the link to the voice recording"
}
