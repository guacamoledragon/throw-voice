package tech.gdragon.commands.audio

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang

class Stop : CommandHandler() {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent, pawa: Pawa) {
    require(args.isEmpty()) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val translator = transaction { Guild[event.guild.idLong].settings.language.let(Babel::stop) }
    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel

    val message =
      if (event.guild.audioManager.isConnected) {
        event.guild.audioManager.connectedChannel?.let {
          val save = BotUtils.autoSave(event.guild)
          BotUtils.leaveVoiceChannel(it, defaultChannel, save)
          ":wave: _${translator.leaveChannel(it.id)}_"
        } ?: ":no_entry_sign: _${translator.noChannel}_"

      } else {
        ":no_entry_sign: _${translator.noChannel}_"
      }

    BotUtils.sendMessage(defaultChannel, message)
  }

  override fun usage(prefix: String, lang: Lang): String {
    val translator = Babel.stop(lang)
    return translator.usage(prefix)
  }

  override fun description(lang: Lang): String = "Ask the bot to stop recording and leave its current channel"
}
