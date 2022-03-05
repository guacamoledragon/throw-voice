package tech.gdragon.h2

import java.io.FileInputStream
import java.util.*

object RunScript {
  @JvmStatic
  fun main(args: Array<String>) {
    val props = Properties().also { p ->
      FileInputStream("settings.properties").use {
        p.load(it)
      }
    }
    println("Loading settings.properties")

    val dataDirectory = props.getProperty("BOT_DATA_DIR")
    val dbName = "embedded-database/settings-v2.db"
    val url = "jdbc:h2:file:$dataDirectory/$dbName"

    val user = ""
    val password = ""
    val fileName = "backup.sql"

    println("Creating new database: $dataDirectory/$dbName")
    org.h2.tools.RunScript.execute(url, user, password, fileName, null, false)
    println("Press Enter key to continue...")
    readLine()
  }
}
