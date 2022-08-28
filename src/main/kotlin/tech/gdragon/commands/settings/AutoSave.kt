package tech.gdragon.commands.settings

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang

class AutoSave : CommandHandler() {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent, pawa: Pawa) {

    require(args.isEmpty()) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val autoSave: Boolean? = transaction {
      Guild
        .findById(event.guild.idLong)
        ?.settings
        ?.apply { autoSave = !autoSave } // Toggle AutoSave
        ?.autoSave
    }

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel

    val translator = transaction { Guild[event.guild.idLong].settings.language.let(Babel::autosave) }

    val message =
      when (autoSave) {
        true -> ":vibration_mode::floppy_disk: _${translator.on}_"
        false -> ":mobile_phone_off::floppy_disk: _${translator.off}_"
        else -> ":no_entry_sign: _${translator.noop}_"
      }

    BotUtils.sendMessage(defaultChannel, message)
  }

  override fun usage(prefix: String, lang: Lang): String = Babel.autosave(lang).usage(prefix)

  override fun description(lang: Lang): String = "Toggles the option to automatically save and send all files at the end of each session - not just saved or clipped files."
}
