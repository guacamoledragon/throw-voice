package tech.gdragon.db

import mu.KotlinLogging
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.joda.time.DateTimeZone
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import org.jetbrains.exposed.sql.Database as ExposedDatabase

interface Database {
  val database: ExposedDatabase?

  fun connect()

  fun shutdown() {
    database?.let(TransactionManager::closeAndUnregister)
  }

  fun migrate(): MigrateResult
}

/**
 * Creates a container for an embedded H2 database.
 *
 * @param url The JDBC URL of the database. eg. "jdbc:h2:file:./settings.db"
 */
class EmbeddedDatabase(private val url: String) : Database {
  val logger = KotlinLogging.logger { }
  private var _database: ExposedDatabase? = null
  override val database = _database

  override fun connect() {
    if (_database != null) {
      return
    }
    _database = ExposedDatabase.connect(url, "org.h2.Driver")
  }

  override fun migrate(): MigrateResult {
    require(_database != null) {
      "Database not initialized"
    }

    val url = _database?.url

    logger.info {
      "Starting database migration: $url"
    }

    val flyway = Flyway.configure()
      .dataSource(url, "", "")
      .baselineOnMigrate(true)
      .locations("h2")
      .load()

    val result = flyway.migrate().also {
      it
        .migrations
        .forEach { migration ->
          logger.info {
            "Performed migration step: ${migration.description}"
          }
        }
    }

    return result
  }
}

class RemoteDatabase(database: String?, hostname: String?, username: String?, password: String?) : Database {
  private var _database: ExposedDatabase? = null
  override val database = _database

  override fun connect() {
    TODO("Not yet implemented")
  }

  override fun migrate(): MigrateResult {
    TODO("Not yet implemented")
  }

  init {
    // Ensure that Joda Time deals with time as UTC
    DateTimeZone.setDefault(DateTimeZone.UTC)
    _database = ExposedDatabase.connect("jdbc:postgresql://$hostname/$database", "org.postgresql.Driver", username!!, password!!)
  }
}
