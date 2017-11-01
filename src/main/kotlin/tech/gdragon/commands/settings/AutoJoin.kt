package tech.gdragon.commands.settings

import mu.KotlinLogging
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.DiscordBot
import tech.gdragon.commands.Command
import tech.gdragon.db.dao.Channel
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.table.Tables.Channels

class AutoJoin : Command {
  private val commandLogger = KotlinLogging.logger {}

  private fun updateChannelAutoJoin(guildId: Long, channelId: Long, autoJoin: Int?) {
    Channel
      .find {
        (Channels.settings eq Guild.findById(guildId)?.settings?.id) and (Channels.id eq channelId)
      }
      .forEach { it.autoJoin = autoJoin }
  }

  /**
   * Sets the autoJoin value for a given voice channel. `null` represents autoJoin for that
   * channel is disabled.
   */
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    transaction {
      val guildId = event.guild.idLong
      val prefix = Guild.findById(guildId)?.settings!!.prefix

      require(args.size >= 2) {
        BotUtils.sendMessage(event.channel, usage(prefix))
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
            channels.forEach { updateChannelAutoJoin(guildId, it.idLong, number) }

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
              channels.forEach { updateChannelAutoJoin(guildId, it.idLong, number) }

              if (number != null) {
                "Will now automatically join '$channelName' when there are $number or more people."
              } else {
                "Will no longer automatically join '$channelName'."
              }
            }
          }
        } catch (e: NumberFormatException) {
          commandLogger.error(e) { "${event.guild.name}: Could not parse number argument." }
          usage(prefix)
        } catch (e: IllegalArgumentException) {
          commandLogger.error(e) { "${event.guild.name}: Number must be positive." }
          e.message
        } ?: usage(prefix)

      DiscordBot.sendMessage(event.channel, message)
    }
  }

  override fun usage(prefix: String): String = "${prefix}autojoin [Voice Channel name | 'all'] [number | 'off']"

  override fun description(): String = "Sets the number of players for the bot to auto-join a voice channel, or " +
    "disables auto-joining. All will apply number to all voice channels."
}
