package tech.gdragon.commands.settings

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.asyncTransaction
import tech.gdragon.db.dao.Guild
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang
import tech.gdragon.i18n.SaveLocation

class SaveLocation : CommandHandler() {
  private fun setSaveLocation(guildId: Long, channel: TextChannel?, translator: SaveLocation): String {
    val settings = transaction {
      Guild[guildId].settings
    }

    return when {
      channel == null -> {
        asyncTransaction { settings.defaultTextChannel = null }
        ":file_folder: _${translator.current}_"
      }

      channel.canTalk() -> {
        asyncTransaction { settings.defaultTextChannel = channel.idLong }
        ":file_folder: _${translator.channel(channel.asMention)}_"
      }

      else -> ":no_entry_sign: _${translator.permissions(channel.asMention)}_"
    }
  }

  override fun action(args: Array<String>, event: MessageReceivedEvent, pawa: Pawa) {
    require(args.size in 0..1) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val guildId = event.guild.idLong

    val translator = transaction {
      Guild[guildId]
        .settings
        .language
        .let(Babel::savelocation)
    }

    val message = when {
      args.isEmpty() -> setSaveLocation(guildId, event.channel.asTextChannel(), translator)
      args.first() == "off" -> setSaveLocation(guildId, null, translator)
      else -> {
        val channelName = if (args.first().startsWith("#")) args.first().substring(1) else args.first()
        val channels = event.guild.getTextChannelsByName(channelName, true)

        if (channels.isEmpty()) {
          ":no_entry_sign: _${translator.notFound(args.first())}_"
        } else {
          setSaveLocation(guildId, channels.first(), translator)
        }
      }
    }

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
    BotUtils.sendMessage(defaultChannel, message)
  }

  override fun usage(prefix: String, lang: Lang): String = Babel.savelocation(lang).usage(prefix)

  override fun description(lang: Lang): String =
    "Sets the text channel to send all messages to, use `off` to restore default behaviour."
}
