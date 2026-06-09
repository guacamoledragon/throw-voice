package tech.gdragon.commands.settings

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.Command
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.i18n.AutoSave as AutoSaveTranslator

object AutoSave {
  val command = Command("autosave", "Toggle setting to save all recordings automatically.")

  fun slashHandler(pawa: Pawa): suspend CoroutineEventListener.(GenericCommandInteractionEvent) -> Unit = { event ->
    event.guild?.let { guild ->
      event.reply(handler(pawa, guild.idLong)).await()
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
