package tech.gdragon

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.Slf4jSqlLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.db.dao.Alias
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.dao.Settings
import tech.gdragon.db.table.Tables
import java.sql.Connection

fun main(args: Array<String>) {
  Database.connect("jdbc:sqlite:settings.db", driver = "org.sqlite.JDBC")
  TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_READ_UNCOMMITTED


  transaction {
    logger.addLogger(Slf4jSqlLogger)

    SchemaUtils.drop(*Tables.allTables)
    SchemaUtils.create(*Tables.allTables)

//    val guild = Guild[333055724198559745L]

    val alias = Alias.new {
      name = "info"
      alias = "help"
    }

    val guild = Guild.new() {
      name = "Guacamole Dragon"
      settings = Settings.new {
        autoSave = false
      }
    }

    guild.settings.aliases = SizedCollection(listOf(alias))

    println(guild.settings.autoSave)
    guild.settings.aliases.forEach { println("${it.name} -> ${it.alias}") }
  }
}
