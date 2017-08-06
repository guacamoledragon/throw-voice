package tech.gdragon

import org.jetbrains.exposed.sql.Database

fun main(args: Array<String>) {
  Database.connect("jdbc:sqlite:settings.db", driver = "org.sqlite.JDBC")

}
