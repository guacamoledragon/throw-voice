package tech.gdragon

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.table.Guilds
import tech.gdragon.db.table.Settings
import java.sql.Connection

fun main(args: Array<String>) {
//  Database.connect("jdbc:h2:mem:db", driver = "org.h2.Driver")
  Database.connect("jdbc:sqlite:settings.db", driver = "org.sqlite.JDBC")
  TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_READ_UNCOMMITTED


  transaction {
    logger.addLogger(StdOutSqlLogger)

    SchemaUtils.create(Guilds, Settings)

    val guild = Guild[333055724198559745L]

    /*val guild = Guild.new(333055724198559745L) {
      name = "Guacamole Dragon"
      settings = Settings.new {
        autoSave = false
      }
    }*/
    println(guild.settings.autoSave)
//    drop(Guilds, SettingsTable)
  }
}
