package tech.gdragon.h2

import java.io.File
import java.io.FileInputStream
import java.util.Properties

object Script {
  @JvmStatic
  fun main(args: Array<String>) {
    val props = Properties().also { p ->
      FileInputStream("settings.properties").use {
        p.load(it)
      }
    }
    println("Loading settings.properties")

    val dataDirectory = props.getProperty("BOT_DATA_DIR")
    val dbName = "embedded-database/settings.db"
    val url = "jdbc:h2:file:$dataDirectory/$dbName"

    val user = ""
    val password = ""
    val fileName = "backup.sql"

    println("Generating backup for Database: $dataDirectory/$dbName")
    org.h2.tools.Script.process(url, user, password, fileName, "", "")

    if (File(fileName).exists()) {
      println("Successfully created backup.sql backup script!")
    } else {
      println("Couldn't create backup script :(.")
    }

    println("Press Enter key to continue...")
    readLine()
  }
}
