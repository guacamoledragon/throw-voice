package tech.gdragon.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Connection

fun initializeDatabase(database: String) {
  Database.connect("jdbc:sqlite:$database", driver = "org.sqlite.JDBC", setupConnection = {
    val statement = it.createStatement()
    statement.executeUpdate("PRAGMA foreign_keys = ON")
  })
  TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_READ_UNCOMMITTED
}
