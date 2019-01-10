package tech.gdragon.commands.settings

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.db.dao.Channel
import tech.gdragon.db.dao.Guild
import net.dv8tion.jda.core.entities.Channel as DiscordChannel

class AutoLeave : CommandHandler {
  private fun updateChannelAutoLeave(channel: DiscordChannel, autoLeave: Int) {
    transaction {
      val guild = channel.guild.run {
        Guild.findOrCreate(idLong, name, region.name)
      }

      Channel
        .findOrCreate(channel.idLong, channel.name, guild)
//        .forEach { it.autoLeave = autoLeave }
    }
  }

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    /*require(args.size >= 2) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val message =
      try {
        val channelName = args.dropLast(1).joinToString(" ")
        val number: Int = args.last().toInt()

        check(number > 0) {
          "Number must be positive!"
        }

        if (channelName == "all") {
          val channels = event.guild.voiceChannels
          channels.forEach { updateChannelAutoLeave(it, number) }
          "Will now automatically leave any voice channel with $number or less people."
        } else {
          val channels = event.guild.getVoiceChannelsByName(channelName, true)

          if (channels.isEmpty()) {
            "Cannot find voice channel $channelName."
          } else {
            channels.forEach { updateChannelAutoLeave(it, number) }
            "Will now automatically leave '$channelName' when there are $number or less people."
          }
        }
      } catch (e: NumberFormatException) {
        throw InvalidCommand(::usage, "Could not parse number argument: ${e.message}")
      } catch (e: IllegalArgumentException) {
        throw InvalidCommand(::usage, "Number must be positive: ${e.message}")
      }*/

    BotUtils.sendMessage(event.channel, ":no_entry_sign: _AutoLeave is currently disabled due to some bugs_")
  }

  override fun usage(prefix: String): String = "${prefix}autoleave [Voice Channel name | 'all'] [number]"

  override fun description(): String = "Sets the number of players for the bot to auto-leave a voice channel. All will apply number to all voice channels."
}
