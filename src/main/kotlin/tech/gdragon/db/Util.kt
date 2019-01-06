package tech.gdragon.db

import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.not
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import tech.gdragon.db.table.Tables

fun nowUTC(): DateTime = DateTime.now()

/**
 * Removes any Guild that hasn't been active in the past 30 days.
 */
fun removeAncientGuilds() {
  transaction {
    Tables.Guilds.deleteWhere {
      val now = DateTime.now()
      not(Tables.Guilds.lastActiveOn.between(now.minusDays(30), now))
    }
  }
}
