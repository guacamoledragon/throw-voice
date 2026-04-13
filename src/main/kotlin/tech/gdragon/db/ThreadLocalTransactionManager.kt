@file:JvmName("ThreadLocalTransactionManager")

package tech.gdragon.db

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction as txn
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.function.Supplier

val logger = KotlinLogging.logger { }

val threadPool: ExecutorService = Executors.newSingleThreadExecutor()

fun <T> asyncTransaction(db: Database? = null, statement: JdbcTransaction.() -> T): Future<T?> {
  val supplier = Supplier<T> {
    txn(db, statement = statement)
  }

  return CompletableFuture
    .supplyAsync(supplier, threadPool)
    .exceptionally { t ->
      logger.error(t) { "Failed to run asyncTransaction" }
      null
    }
}
