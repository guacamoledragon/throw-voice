package tech.gdragon.api.pawa

import mu.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.db.Database
import tech.gdragon.db.dao.Alias
import tech.gdragon.db.dao.Settings
import tech.gdragon.db.table.Tables
import tech.gdragon.discord.Command

class Pawa(val db: Database) {
  val logger = KotlinLogging.logger { }

  fun createAlias(guildId: Long, command: Command, alias: String): Alias? =
    // If the alias isn't the name of an existing command, create a new alias
    when (Command.values().none { it.name.lowercase() == alias.lowercase() }) {
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
