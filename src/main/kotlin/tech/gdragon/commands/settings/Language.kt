package tech.gdragon.commands.settings

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.i18n.Lang
import java.lang.IllegalArgumentException

class Language : CommandHandler() {
  companion object {
    var currentLanguage = Lang.EN

    fun valid(lang: String): Boolean {
      return try {
        Lang.valueOf(lang)
        true
      } catch (ex: IllegalArgumentException) {
        false
      }
    }
  }

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    val lang = args.firstOrNull()?.toUpperCase() ?: ""

    require (args.isNotEmpty() && valid(lang)) {
      throw InvalidCommand(::usage, "Expected one of: ${Lang.values().joinToString { it.name }}, but got $lang")
    }

    currentLanguage = Lang.valueOf(lang)
  }

  override fun usage(prefix: String): String = "${prefix}lang [${Lang.values().joinToString(separator = "|") { it.name.toLowerCase() }}]"

  override fun description(): String = "Specifies the language to use."
}
