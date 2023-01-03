package tech.gdragon.commands.settings

import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.Command
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang
import tech.gdragon.i18n.AutoSave as AutoSaveTranslator

class AutoSave : CommandHandler() {
  companion object {
    val command = Command("autosave", "Toggle setting to save all recordings automatically.")

    fun slashHandler(pawa: Pawa): suspend CoroutineEventListener.(GenericCommandInteractionEvent) -> Unit = { event ->
      event.guild?.let { guild ->
        event.reply(handler(pawa, guild.idLong)).queue()
      }
    }

    private fun handler(pawa: Pawa, guildId: Long): String {
      val translator: AutoSaveTranslator = pawa.translator(guildId)

      val message =
        when (pawa.toggleAutoSave(guildId)) {
          true -> ":vibration_mode::floppy_disk: _${translator.on}_"
          false -> ":mobile_phone_off::floppy_disk: _${translator.off}_"
          else -> ":no_entry_sign: _${translator.noop}_"
        }

      return message
    }
  }

  override fun action(args: Array<String>, event: MessageReceivedEvent, pawa: Pawa) {
    require(args.isEmpty()) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel

    val message = handler(pawa, event.guild.idLong)

    BotUtils.sendMessage(defaultChannel, message)
  }

  override fun usage(prefix: String, lang: Lang): String = Babel.autosave(lang).usage(prefix)

  override fun description(lang: Lang): String =
    "Toggles the option to automatically save and send all files at the end of each session - not just saved or clipped files."
}
