package tech.gdragon.commands.settings

import mu.withLoggingContext
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.asyncTransaction
import tech.gdragon.db.dao.Channel
import tech.gdragon.db.dao.Guild
import tech.gdragon.i18n.Lang

class AutoRecord : CommandHandler() {
  private fun updateChannelAutoJoin(channel: GuildChannel, autoRecord: Int?) {
    withLoggingContext("guild" to channel.guild.name, "text-channel" to channel.name) {
      channel.guild.run {
        transaction {
          Guild.findOrCreate(idLong, name, region.name)
        }
      }.let { guild ->
        asyncTransaction {
          Channel
            .findOrCreate(channel.idLong, channel.name, guild)
            .also { it.autoRecord = autoRecord }
        }
      }
    }
  }

  /**
   * Sets the autoRecord value for a given voice channel. `null` represents autoRecord for that
   * channel is disabled.
   * TODO: Minor optimization, delete rows that have the defaults
   */
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent, pawa: Pawa) {
    require(standalone) {
      BotUtils.sendMessage(
        event.channel,
        ":no_entry_sign: _Command is currently disabled, please see https://pawa.im/#/commands for more information._"
      )
      return
    }
    require(args.size >= 2) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
    val message: String =
      try {
        val channelName = args.dropLast(1).joinToString(" ")
        val number: Int? = when (args.last()) {
          "off" -> null
          "0" -> null
          else -> {
            val lastArg = args.last().toInt()

            if (lastArg < 0)
              throw InvalidCommand(::usage, "Number must be positive: $lastArg")
            else
              lastArg
          }
        }

        if (channelName == "all") {
          val channels = event.guild.voiceChannels
          channels.forEach { updateChannelAutoJoin(it, number) }

          if (number != null) {
            ":vibration_mode::red_circle: _Will automatically record any voice channel with **$number** or more people._"
          } else {
            ":mobile_phone_off::red_circle: _Will not automatically record any channel._"
          }
        } else {
          val channels = event.guild.getVoiceChannelsByName(channelName, true)

          if (channels.isEmpty()) {
            ":no_entry_sign: _Cannot find voice channel **#$channelName**._"
          } else {
            channels.forEach { updateChannelAutoJoin(it, number) }
            val voiceChannel = channels.first()

            if (number != null) {
              ":vibration_mode::red_circle: _Will automatically record on **<#${voiceChannel.id}>** when there are **$number** or more people._"
            } else {
              ":mobile_phone_off::red_circle: _Will not automatically record **<#${voiceChannel.id}>**._"
            }
          }
        }
      } catch (e: NumberFormatException) {
        throw InvalidCommand(::usage, "Could not parse number argument: ${e.message}")
      }

    BotUtils.sendMessage(defaultChannel, message)
  }

  override fun usage(prefix: String, lang: Lang): String = "${prefix}autorecord [Voice Channel name | 'all'] [number | 'off']"

  override fun description(lang: Lang): String = "Sets the number of players for the bot to autorecord a voice channel, or " +
    "disables auto recording. `All` will apply number to all voice channels."
}
