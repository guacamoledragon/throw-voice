package tech.gdragon.db

import org.jetbrains.exposed.sql.Database
import org.joda.time.DateTimeZone

fun initializeDatabase(database: String?, hostname: String?, username: String?, password: String?) {
  // Ensure that Joda Time deals with time as UTC
  DateTimeZone.setDefault(DateTimeZone.UTC)

  Database.connect("jdbc:postgresql://$hostname/$database", "org.postgresql.Driver", username!!, password!!)
//  Database.connect("jdbc:pgsql://$hostname/$database", "com.impossibl.postgres.jdbc.PGDriver" ,username, password)
}
