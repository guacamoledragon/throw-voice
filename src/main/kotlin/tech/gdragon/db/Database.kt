package tech.gdragon.db

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.jetbrains.exposed.sql.Database as ExposedDatabase
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTimeZone
import tech.gdragon.db.table.Tables

interface Database {
  fun stop()
}

class EmbeddedDatabase(dataDirectory: String) : Database {
  var pg: EmbeddedPostgres = EmbeddedPostgres
    .builder()
    .setCleanDataDirectory(false)
    .setDataDirectory(dataDirectory)
    .start()

  init {
    // Source: https://stackoverflow.com/q/32336651
    val db = pg.getPostgresDatabase(mapOf("stringtype" to "unspecified"))
    ExposedDatabase.connect(db::getConnection)

    transaction {
      SchemaUtils.create(*Tables.allTables)
    }
  }

  override fun stop() {
    pg.close()
  }
}

class RemoteDatabase(database: String?, hostname: String?, username: String?, password: String?) : Database {
  init {
    // Ensure that Joda Time deals with time as UTC
    DateTimeZone.setDefault(DateTimeZone.UTC)
    ExposedDatabase.connect("jdbc:postgresql://$hostname/$database", "org.postgresql.Driver", username!!, password!!)
  }

  override fun stop() {
    // Do nothing...
  }
}
