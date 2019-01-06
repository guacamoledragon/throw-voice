package tech.gdragon.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTimeZone
import java.sql.Connection

/**
 * A temporary shim for interacting with the database from Java land as the rest of the codebase is ported to Kotlin
 */
object Shim {
  fun initializeDatabase(database: String) {
    // Ensure that Joda Time deals with time as UTC
    DateTimeZone.setDefault(DateTimeZone.UTC)

    Database.connect("jdbc:sqlite:$database", driver = "org.sqlite.JDBC", setupConnection = {
      val statement = it.createStatement()
      statement.executeUpdate("PRAGMA foreign_keys = ON")
    })
    TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_READ_UNCOMMITTED
  }

  /**
   * `org.jetbrains.exposed.sql.transactions.transaction` wrapper
   */
  fun <T> xaction(ctx: () -> T): T {
    return transaction {
      return@transaction ctx()
    }
  }
}
