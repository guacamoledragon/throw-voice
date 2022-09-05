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

class Pawa(val db: Database) {
  val logger = KotlinLogging.logger { }

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

  fun autoStopChannels(guildId: Long, channels: List<Pair<Long, String>>, threshold: Int) {
    transaction(db.database) {
      channels
        .map { (id, name) -> Channel.findOrCreate(id, name, guildId) }
        .forEach { channel ->
          channel.autoStop = threshold
        }
    }
  }
}
