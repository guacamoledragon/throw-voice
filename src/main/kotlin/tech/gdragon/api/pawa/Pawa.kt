package tech.gdragon.api.pawa

import mu.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.db.Database
import tech.gdragon.db.dao.Alias
import tech.gdragon.db.dao.Channel
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.dao.Settings
import tech.gdragon.db.table.Tables
import tech.gdragon.discord.Command
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang

class Pawa(val db: Database) {
  val logger = KotlinLogging.logger { }

  private var _ignoredUsers: MutableMap<String, List<Long>> = mutableMapOf()

  inline fun <reified T> translator(guildId: Long): T {
    val lang = transaction { Guild[guildId].settings.language }
    return Babel.commandTranslator(lang)
  }

  fun createAlias(guildId: Long, command: Command, alias: String): Alias? {
    // Command cannot be ALIAS and the alias cannot be the name of an existing command
    val isValidAlias = (Command.ALIAS != command) &&
      Command
        .values()
        .map { it.name.lowercase() }
        .none { it == alias.lowercase() }

    return when (isValidAlias) {
      true -> transaction(db.database) {
        Settings
          .find { Tables.Settings.guild eq guildId }
          .firstOrNull()
          ?.let { settings ->
            Alias.findOrCreate(command.name, alias, settings)
          }
      }

      // Otherwise, don't create a new alias
      false -> {
        logger.warn {
          "Could not create alias for ${command.name}"
        }
        null
      }
    }
  }

  fun autoStopChannel(channelId: Long, channelName: String, guildId: Long, threshold: Long) {
    transaction(db.database) {
      Channel.findOrCreate(channelId, channelName, guildId).autoStop = threshold.toInt()
    }
  }

  fun toggleAutoSave(guildId: Long): Boolean? {
    return transaction(db.database) {
      Settings
        .find { Tables.Settings.guild eq guildId }
        .firstOrNull()
        ?.apply {
          autoSave = !autoSave
        }
        ?.autoSave
    }
  }

  fun setLocale(guildId: Long, locale: String): Pair<Lang, Lang> {
    return transaction {
      val guild = Guild[guildId]
      val prev = guild.settings.language
      val newLang = Lang.valueOf(locale)

      guild.settings.language = newLang

      Pair(prev, newLang)
    }
  }

  fun ignoreUsers(session: String, ignoredUserIds: List<Long>) {
    _ignoredUsers[session] = ignoredUserIds
  }
}
