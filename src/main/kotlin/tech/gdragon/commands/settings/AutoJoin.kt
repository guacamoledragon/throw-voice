package tech.gdragon.commands.settings

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.Command
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Channel
import tech.gdragon.db.dao.Guild
import net.dv8tion.jda.core.entities.Channel as DiscordChannel

class AutoJoin : Command {
  private fun updateChannelAutoJoin(channel: DiscordChannel, autoJoin: Int?) {
    transaction {
      val guild = channel.guild.run {
        Guild.findOrCreate(idLong, name, region.name)
      }

      Channel
        .findOrCreate(channel.idLong, channel.name, guild)
        .also { it.autoJoin = autoJoin }
    }
  }

  /**
   * Sets the autoJoin value for a given voice channel. `null` represents autoJoin for that
   * channel is disabled.
   */
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.size >= 2) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val message: String =
      try {
        val channelName = args.dropLast(1).joinToString(" ")
        val number: Int? = when (args.last()) {
          "off" -> null
          "0" -> null
          else -> {
            val lastArg = args.last().toInt()

            if (lastArg < 0)
              throw IllegalArgumentException("Number must be positive!")
            else
              lastArg
          }
        }

        if (channelName == "all") {
          val channels = event.guild.voiceChannels
          channels.forEach { updateChannelAutoJoin(it, number) }

          if (number != null) {
            "Will now automatically join any voice channel with $number or more people."
          } else {
            "Will no longer automatically join any channel."
          }
        } else {
          val channels = event.guild.getVoiceChannelsByName(channelName, true)

          if (channels.isEmpty()) {
            "Cannot find voice channel $channelName."
          } else {
            channels.forEach { updateChannelAutoJoin(it, number) }

            if (number != null) {
              "Will now automatically join '$channelName' when there are $number or more people."
            } else {
              "Will no longer automatically join '$channelName'."
            }
          }
        }
      } catch (e: NumberFormatException) {
        throw InvalidCommand(::usage, "Could not parse number argument: ${e.message}")
      } catch (e: IllegalArgumentException) {
        throw InvalidCommand(::usage, "Number must be positive: ${e.message}")
      }

    BotUtils.sendMessage(event.channel, message)
  }

  override fun usage(prefix: String): String = "${prefix}autojoin [Voice Channel name | 'all'] [number | 'off']"

  override fun description(): String = "Sets the number of players for the bot to auto-join a voice channel, or " +
    "disables auto-joining. All will apply number to all voice channels."
}
