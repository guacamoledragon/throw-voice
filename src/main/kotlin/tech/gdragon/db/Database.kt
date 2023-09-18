package tech.gdragon.db

import io.github.oshai.kotlinlogging.KotlinLogging
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.joda.time.DateTimeZone
import org.koin.dsl.module
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

val databaseModule = module {
  single<Database>(createdAtStart = true) {
    logger.info("Creating Database Module")
    if (getProperty<String>("BOT_STANDALONE").toBoolean()) {
      val dataDirectory = getProperty("BOT_DATA_DIR", "./")
      val dbPath = "$dataDirectory/embedded-database"
      Path(dbPath).createDirectories()
      val url = "jdbc:h2:file:$dbPath/settings.db"

      EmbeddedDatabase(url).apply {
        connect()
        migrate()
      }
    } else {
      val host: String = getProperty("DB_HOST")
      val dbName: String = getProperty("DB_NAME")
      val url = "jdbc:postgresql://$host/$dbName"

      RemoteDatabase(
        url,
        getProperty("DB_USER"),
        getProperty("DB_PASSWORD")
      ).apply {
        connect()
      }
    }
  }
}
