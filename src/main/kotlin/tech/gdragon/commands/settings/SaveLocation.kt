package tech.gdragon.commands.settings

import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.asyncTransaction
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.dao.Settings
import tech.gdragon.i18n.Lang

class SaveLocation : CommandHandler() {
  private fun setSaveLocation(settings: Settings, channel: TextChannel?): String {
    return when {
      channel == null -> {
        asyncTransaction { settings.defaultTextChannel = null }
        ":file_folder: _All messages will default to current channel._"
      }
      channel.canTalk() -> {
        asyncTransaction { settings.defaultTextChannel = channel.idLong }
        ":file_folder: _All messages will default to channel **${channel.asMention}**._"
      }
      else -> ":no_entry_sign: _Cannot send messages in **${channel.asMention}**, please configure permissions and try again._"
    }
  }

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.size in 0..1) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val settings = transaction {
      Guild.findById(event.guild.idLong)?.settings
    }

    val message = settings?.let {
      when {
        args.isEmpty() -> setSaveLocation(it, event.channel)
        args.first() == "off" -> setSaveLocation(it, null)
        else -> {
          val channelName = if (args.first().startsWith("#")) args.first().substring(1) else args.first()
          val channels = event.guild.getTextChannelsByName(channelName, true)

          if (channels.isEmpty()) {
            ":no_entry_sign: _Cannot find text channel **${args.first()}**!_"
          } else {
            setSaveLocation(it, channels.first())
          }
        }
      }
    } ?: ":no_entry_sign: _Could not set default save location._"

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
    BotUtils.sendMessage(defaultChannel, message)
  }

  override fun usage(prefix: String, lang: Lang): String = "${prefix}saveLocation | ${prefix}saveLocation [text channel | 'off']"

  override fun description(lang: Lang): String = "Sets the text channel to send all messages to, use `off` to restore default behaviour."
}
