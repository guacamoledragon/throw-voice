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

class EmbeddedDatabase(dataDirectory: String) : Database {
  val logger = KotlinLogging.logger { }
  private var _database: ExposedDatabase? = null
  override val database = _database

  init {
    val dbPath = "$dataDirectory/embedded-database"
    Path(dbPath).createDirectories()
    val url = "jdbc:h2:file:$dbPath/settings.db"
    logger.info {
      "Starting database migration: $url"
    }
    val flyway = Flyway.configure()
      .dataSource(url, "", "")
      .baselineOnMigrate(true)
      .locations("h2")
      .load()

    flyway
      .migrate()
      .migrations
      .forEach { migration ->
        logger.info {
          "Performed migration step: ${migration.description}"
        }
      }
  override fun connect() {
    TODO("Not yet implemented")
  }

    _database = ExposedDatabase.connect(url, "org.h2.Driver")
  override fun migrate(): MigrateResult {
    TODO("Not yet implemented")
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
