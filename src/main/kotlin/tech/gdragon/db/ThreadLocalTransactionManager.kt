@file:JvmName("ThreadLocalTransactionManager")

package tech.gdragon.db

import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.function.Supplier
import org.jetbrains.exposed.sql.transactions.transaction as txn

val logger = KotlinLogging.logger { }

val threadPool: ExecutorService = Executors.newSingleThreadExecutor()

fun <T> asyncTransaction(db: Database? = null, statement: Transaction.() -> T): Future<T?> {
  val supplier = Supplier<T> {
    txn(
      db.transactionManager.defaultIsolationLevel,
      db.transactionManager.defaultReadOnly,
      db,
      statement
    )
  }

  return CompletableFuture
    .supplyAsync(supplier, threadPool)
    .exceptionally { t ->
      logger.error(t) { "Failed to run asyncTransaction" }
      null
    }
}
