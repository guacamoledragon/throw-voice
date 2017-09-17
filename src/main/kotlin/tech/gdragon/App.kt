package tech.gdragon

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.db.dao.Alias
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.dao.Settings
import tech.gdragon.db.table.Tables
import java.sql.Connection

fun main(args: Array<String>) {
  val database = "settings.db"
  Database.connect("jdbc:sqlite:$database", driver = "org.sqlite.JDBC")
  TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_READ_UNCOMMITTED

  val aliases: List<Alias> = transaction {
    SchemaUtils.drop(*Tables.allTables)
    SchemaUtils.create(*Tables.allTables)

//    val guild = Guild[333055724198559745L]

    val guild = Guild.new {
      name = "Guacamole Dragon"
      settings = Settings.new {}
    }

    Alias.createDefaultAliases(guild.settings)

    println(guild.settings.autoSave)
    guild.settings.aliases.forEach { println("${it.name} -> ${it.alias}") }

    return@transaction guild.settings.aliases.toList()
  }

  aliases.forEach { println("${it.name} -> ${it.alias}") }
}
