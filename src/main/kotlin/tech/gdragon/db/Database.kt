package tech.gdragon.db

import mu.KotlinLogging
import org.flywaydb.core.Flyway
import org.joda.time.DateTimeZone
import org.jetbrains.exposed.sql.Database as ExposedDatabase

interface Database

class EmbeddedDatabase(dataDirectory: String) : Database {
  val logger = KotlinLogging.logger { }

  init {
    val url = "jdbc:h2:file:$dataDirectory/settings.db"
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

    ExposedDatabase.connect(url, "org.h2.Driver")
  }
}

class RemoteDatabase(database: String?, hostname: String?, username: String?, password: String?) : Database {
  init {
    // Ensure that Joda Time deals with time as UTC
    DateTimeZone.setDefault(DateTimeZone.UTC)
    ExposedDatabase.connect("jdbc:postgresql://$hostname/$database", "org.postgresql.Driver", username!!, password!!)
  }
}
