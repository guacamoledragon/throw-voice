@file:JvmName("ThreadLocalTransactionManager")

package tech.gdragon.db

import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import org.jetbrains.exposed.sql.transactions.transaction as txn

val logger = KotlinLogging.logger { }

val lock = Object()

fun <T> asyncTransaction(db: Database? = null, statement: Transaction.() -> T): Future<T> {
  return CompletableFuture.supplyAsync {
    synchronized(lock) {
      txn(db.transactionManager.defaultIsolationLevel, db.transactionManager.defaultRepetitionAttempts, db, statement)
    }
  }.exceptionally { t ->
    logger.error(t) {
      "Failed to run asyncTransaction"
    }

    null
  }
}
