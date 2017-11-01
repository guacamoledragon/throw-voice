package tech.gdragon.commands.settings

import mu.KotlinLogging
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.Command
import tech.gdragon.db.dao.Channel
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.table.Tables

class AutoLeave : Command {
  private val commandLogger = KotlinLogging.logger {}

  private fun updateChannelAutoLeave(guildId: Long, channelId: Long, autoLeave: Int) {
    Channel
      .find {
        (Tables.Channels.settings eq Guild.findById(guildId)?.settings?.id) and (Tables.Channels.id eq channelId)
      }
      .forEach { it.autoLeave = autoLeave }
  }

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    transaction {
      val guildId = event.guild.idLong
      val guild = Guild.findById(guildId)
      val prefix = guild?.settings?.prefix ?: "!"

      require(args.size >= 2) {
        BotUtils.sendMessage(event.channel, usage(prefix))
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
            channels.forEach { updateChannelAutoLeave(guildId, it.idLong, number) }
            "Will now automatically leave any voice channel with $number or more people."
          } else {
            val channels = event.guild.getVoiceChannelsByName(channelName, true)

            if (channels.isEmpty()) {
              "Cannot find voice channel $channelName."
            } else {
              channels.forEach { updateChannelAutoLeave(guildId, it.idLong, number) }
              "Will now automatically leave '$channelName' when there are $number or more people."
            }
          }
        } catch (e: NumberFormatException) {
          commandLogger.error(e) { "${event.guild.name}: Could not parse number argument." }
          usage(prefix)
        } catch (e: IllegalStateException) {
          commandLogger.error(e) { "${event.guild.name}: Number must be positive." }
          e.message
        } ?: usage(prefix)

      BotUtils.sendMessage(event.channel, message)
    }
  }

  override fun usage(prefix: String): String = "${prefix}autoleave [Voice Channel name | 'all'] [number]"

  override fun description(): String = "Sets the number of players for the bot to auto-leave a voice channel, or disables auto-leaving.  All will apply number to all voice channels."
}
