package tech.gdragon.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import tech.gdragon.db.table.Tables
import java.sql.Connection
import java.util.function.Function

/**
 * A temporary shim for interacting with the database from Java land as the rest of the codebase is ported to Kotlin
 */
object Shim {
  fun initializeDatabase(database: String) {
    Database.connect("jdbc:sqlite:${database}", driver = "org.sqlite.JDBC")
    TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_READ_UNCOMMITTED

    org.jetbrains.exposed.sql.transactions.transaction {
      SchemaUtils.create(*Tables.allTables)
    }
  }

  fun transaction(ctx: () -> Void) {
    org.jetbrains.exposed.sql.transactions.transaction {
      ctx()
    }
  }
}
