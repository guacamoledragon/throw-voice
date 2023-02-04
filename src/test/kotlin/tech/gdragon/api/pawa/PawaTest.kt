package tech.gdragon.api.pawa

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.context.startKoin
import tech.gdragon.db.Database
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.databaseModule
import tech.gdragon.discord.Command

class PawaTest : FunSpec({
  lateinit var pawa: Pawa

  val guildId = 1L

  context("when alias") {
    test("doesn't exist, it should be created") {
      val alias = pawa.createAlias(guildId, Command.RECORD, "r")
      alias.shouldNotBeNull()
      alias.alias.shouldBe("r")
      alias.name.shouldBe("RECORD")
    }

    test("exists, it shouldn't be created") {
      val alias = pawa.createAlias(guildId, Command.RECORD, "r")
      alias.shouldNotBeNull()
    }

    test("command is \"alias\" it shouldn't be created") {
      val alias = pawa.createAlias(guildId, Command.ALIAS, "a")
      alias.shouldBeNull()
    }

    test("is a command name, it shouldn't be created") {
      val alias = pawa.createAlias(guildId, Command.RECORD, "record")
      alias.shouldBeNull()
    }
  }

  beforeSpec {
    val app = startKoin {
      koin.apply{
        setProperty("BOT_STANDALONE", "true")
        setProperty("BOT_DATA_DIR", "./target")
      }
      koin.loadModules(listOf(databaseModule))
    }
    val db: Database = app.koin.get()
    pawa = Pawa(1L, db, false)
    transaction(db.database) {
      Guild.findOrCreate(guildId, "Test Guild")
    }
  }

  afterSpec {
    pawa.db.shutdown()
  }
})
