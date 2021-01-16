package tech.gdragon.db

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTimeZone
import tech.gdragon.db.table.Tables
import org.jetbrains.exposed.sql.Database as ExposedDatabase

interface Database

class EmbeddedDatabase(dataDirectory: String) : Database {
  init {
    ExposedDatabase.connect("jdbc:h2:file:$dataDirectory/settings.db", "org.h2.Driver")
    transaction {
      SchemaUtils.create(*Tables.allTables)
    }
  }
}

class RemoteDatabase(database: String?, hostname: String?, username: String?, password: String?) : Database {
  init {
    // Ensure that Joda Time deals with time as UTC
    DateTimeZone.setDefault(DateTimeZone.UTC)
    ExposedDatabase.connect("jdbc:postgresql://$hostname/$database", "org.postgresql.Driver", username!!, password!!)
  }
}
