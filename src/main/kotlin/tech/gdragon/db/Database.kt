package tech.gdragon.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.joda.time.DateTimeZone
import java.sql.Connection

fun initializeDatabase(database: String) {
  // Ensure that Joda Time deals with time as UTC
  DateTimeZone.setDefault(DateTimeZone.UTC)

  /*val setupConnection: (Connection) -> Unit = {
    val statement = it.createStatement()
    statement.executeUpdate("PRAGMA foreign_keys = ON")
  }

  Database.connect("jdbc:sqlite:$database", driver = "org.sqlite.JDBC", setupConnection = setupConnection)
  // Override default isolation level
  TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_READ_UNCOMMITTED*/
  Database.connect("jdbc:postgresql://localhost:5432/postgres", "org.postgresql.Driver" ,"postgres", "password")
//  Database.connect("jdbc:pgsql://localhost:5432/postgres", "com.impossibl.postgres.jdbc.PGDriver" ,"postgres", "password")
}
