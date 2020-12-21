package tech.gdragon.db

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTimeZone
import org.koin.core.component.KoinComponent
import tech.gdragon.db.table.Tables

fun initializeDatabase(database: String?, hostname: String?, username: String?, password: String?) {
  // Ensure that Joda Time deals with time as UTC
  DateTimeZone.setDefault(DateTimeZone.UTC)

  Database.connect("jdbc:postgresql://$hostname/$database", "org.postgresql.Driver", username!!, password!!)
//  Database.connect("jdbc:pgsql://$hostname/$database", "com.impossibl.postgres.jdbc.PGDriver" ,username, password)
}

class DatabaseLite : KoinComponent {
  val pgBuilder = EmbeddedPostgres
    .builder()
    .setCleanDataDirectory(false)
    .setDataDirectory("./local-db")

  init {
    val pg = pgBuilder.start()

    // Source: https://stackoverflow.com/q/32336651
    val db = pg.getPostgresDatabase(mapOf("stringtype" to "unspecified"))
    Database.connect(db::getConnection)
    transaction {
      SchemaUtils.create(*Tables.allTables)
    }
  }
}
