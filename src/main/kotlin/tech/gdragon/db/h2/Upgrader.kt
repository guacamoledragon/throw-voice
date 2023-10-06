package tech.gdragon.db.h2

import io.github.oshai.kotlinlogging.KotlinLogging
import org.h2.util.JdbcUtils
import tech.gdragon.utils.IsolatedClassLoader
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.system.exitProcess

/**
 * Utility class to upgrade an H2 database from [h2Version] to the version specified by the classpath.
 *
 * @param h2Version the version the H2 database at [dbFilename]
 * @property dbFilename the path to the H2 database, please omit the `.mv.db` extension.
 */
class Upgrader(private val dbFilename: String, h2Version: String = "2.1.214") {
  val logger = KotlinLogging.logger { }
  private var _classLoader: IsolatedClassLoader
  private var _dbUrl: String

  init {
    val driverUrl = Path("./drivers/h2-${h2Version}.jar").toUri().toURL()
    _classLoader = IsolatedClassLoader(arrayOf(driverUrl))
    _dbUrl = "jdbc:h2:file:${dbFilename}"
  }

  /**
   *
   */
  fun backup(): String {
    val dbPath = Path("$dbFilename.mv.db")

    require(dbPath.exists()) {
      "$dbPath does not exist, cannot backup!"
    }

    val path = "$dbFilename-${System.currentTimeMillis()}-backup.mv.db"
    val dbBackupPath = Path(path)

    val backupCopy = dbPath.copyTo(dbBackupPath)

    logger.info {
      "Backed up $dbPath to $backupCopy."
    }

    return backupCopy.toString()
  }

  fun restore(sqlFilename: String): String {

    val upgradedDbFilename = Path(Path(dbFilename).parent.toString(), "upgraded")
    val url = "jdbc:h2:file:$upgradedDbFilename"
    val sqlStatement = "RUNSCRIPT FROM '$sqlFilename'"
    val connection = JdbcUtils.getConnection(null, url, "", "")
    val statement = connection.createStatement()

    logger.info {
      "Restoring database $upgradedDbFilename from $sqlFilename"
    }

    statement.execute(sqlStatement)
    connection.commit()
    statement.close()
    connection.close()

    logger.info {
      "Restored database $upgradedDbFilename from $sqlFilename"
    }

    return upgradedDbFilename.toString()
  }

  private fun dumpSql(): String {
    val scriptClass = _classLoader.loadClass("org.h2.tools.Script")

    val user = ""
    val password = ""
    val sqlFilename = "$dbFilename.sql"
    val options1 = ""
    val options2 = ""

    logger.info {
      "Dumping SQL from $dbFilename to $sqlFilename"
    }

    scriptClass
      .getDeclaredMethod(
        "process",
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java
      )
      .invoke(null, _dbUrl, user, password, sqlFilename, options1, options2)

    return sqlFilename
  }


  private fun overwrite(upgradedDbFilename: String) {
    println("========================\nTo complete database upgrade we need to overwrite $dbFilename, can we proceed? (Y/N)")
    val input = readlnOrNull()

    input?.let {
      if (it.lowercase() == "y") {
        Path("$upgradedDbFilename.mv.db").moveTo(Path("$dbFilename.mv.db"), overwrite = true)
      } else {
        println("Aborting database upgrade, cannot proceed.")
        exitProcess(1)
      }
    } ?: {
      println("Aborting database upgrade, cannot proceed.")
      exitProcess(1)
    }
  }

  /**
   * Given a ${dbFilename} and a target version, this will attempt to upgrade the database.
   */
  fun upgrade() {
    val backupDbFilename = backup()
    val sqlFilename = dumpSql()
    val upgradedDbFilename = restore(sqlFilename)
    overwrite(upgradedDbFilename)
    logger.info {
      "Upgraded complete!\nA backup of your original database has been copied to $backupDbFilename"
    }
  }
}

object H2Upgrade {
  val logger = KotlinLogging.logger { }

  @JvmStatic
  fun main(args: Array<String>) {
    val dbFilename = "./pawalite-data/embedded-database/settings.db"
    val upgrader = Upgrader(dbFilename)
    upgrader.upgrade()
  }
}
