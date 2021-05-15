package tech.gdragon.commands.settings

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild
import tech.gdragon.i18n.Lang

class Language : CommandHandler() {
  companion object {
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

    require(args.isNotEmpty() && valid(lang)) {
      throw InvalidCommand(::usage, "Expected one of: ${Lang.values().joinToString { it.name }}, but got $lang")
    }

    transaction {
      val guild = Guild[event.guild.idLong]
      guild.settings.language = Lang.valueOf(lang)
    }

  }

  override fun usage(prefix: String, lang: Lang): String =
    "${prefix}lang [${Lang.values().joinToString(separator = "|") { it.name.toLowerCase() }}]"

  override fun description(lang: Lang): String = "Specifies the language to use."
}
