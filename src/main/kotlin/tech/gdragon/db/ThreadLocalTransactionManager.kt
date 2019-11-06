package tech.gdragon.db

import mu.KotlinLogging
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.transactionManager
import java.sql.SQLException
import org.jetbrains.exposed.sql.transactions.transaction as txn

val logger = KotlinLogging.logger { }

fun <T> transaction(db: Database? = null, statement: Transaction.() -> T): T? {
  return try {
    txn(db.transactionManager.defaultIsolationLevel, db.transactionManager.defaultRepetitionAttempts, db, statement)
  } catch (e: SQLException) {
    val exposedSQLException = e as? ExposedSQLException
    val transaction = db.transactionManager.newTransaction(db.transactionManager.defaultIsolationLevel, null)

    val queriesToLog = exposedSQLException?.contexts?.joinToString("\n") {
      it.expandArgs(transaction)
    }

    logger.error(e) {
      "Failed to run transaction: $queriesToLog"
    }

    null
  }
}
