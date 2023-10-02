package tech.gdragon.db

import io.github.oshai.kotlinlogging.KotlinLogging
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.joda.time.DateTimeZone
import org.koin.dsl.module
import tech.gdragon.db.h2.Upgrader
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import org.jetbrains.exposed.sql.Database as ExposedDatabase

interface Database {
  companion object {
    fun module(isEmbedded: Boolean) = module(createdAtStart = true) {
      single<Database> {
        if (isEmbedded) {
          logger.info("Creating Embedded Database Module")
          val dataDirectory: String = getProperty("BOT_DATA_DIR")
          val dbPath = "$dataDirectory/embedded-database"

          logger.info("Ensure directory exists: $dbPath")
          Path(dbPath).createDirectories()

          val dbFilename = "$dbPath/settings.db"

          EmbeddedDatabase(dbFilename).apply {
            connect()
            upgrade()
            migrate()
          }
        } else {
          logger.info("Creating Remote Database Module")
          val host: String = getProperty("DB_HOST")
          val dbName: String = getProperty("DB_NAME")
          val username: String = getProperty("DB_USER")
          val password: String = getProperty("DB_PASSWORD")

          val url = "jdbc:postgresql://$host/$dbName"

          RemoteDatabase(url, username, password).apply {
            connect()
          }
        }
      }
    }
  }

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
 * @param dbFilename The file path of the database. e.g. "./settings.db"
 */
class EmbeddedDatabase(private val dbFilename: String) : Database {
  val logger = KotlinLogging.logger { }
  private var _database: ExposedDatabase? = null
  override val database = _database

  override fun connect() {
    if (_database != null) {
      return
    }

    val url = "jdbc:h2:file:$dbFilename"
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

  /**
   * Updates the H2 version on the fly.
   */
  fun upgrade() {
    require(_database != null) {
      "Database not initialized"
    }

    try {
      // This will trigger a failure if the database is NOT already at the latest version.
      val dbVersion = _database?.version
      logger.info {
        "Database version: $dbVersion, already up to date."
      }
    } catch (_: Exception) {
      val upgrader = Upgrader(dbFilename)
      upgrader.upgrade()

      // Reconnect to the database to the updated version.
      connect()
    }
  }
}

class RemoteDatabase(val url: String, private val username: String, private val password: String) : Database {
  private var _database: ExposedDatabase? = null
  override val database = _database

//  private val url = "jdbc:postgresql://$hostname/$url"

  override fun connect() {
    if (_database != null) {
      return
    }

    // Ensure that Joda Time deals with time as UTC
    DateTimeZone.setDefault(DateTimeZone.UTC)
    _database =
      ExposedDatabase.connect(url, "org.postgresql.Driver", username, password)
  }

  override fun migrate(): MigrateResult {
    require(_database != null) {
      "Database not initialized"
    }

    logger.info {
      "Starting database migration: $url"
    }

    val flyway = Flyway.configure()
      .dataSource(url, username, password)
      .baselineOnMigrate(true)
      .locations("filesystem:./sql/common", "filesystem:./sql/postgres")
      .load()

    return flyway.migrate()
  }
}
