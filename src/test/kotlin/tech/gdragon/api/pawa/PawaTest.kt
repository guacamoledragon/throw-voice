package tech.gdragon.api.pawa

import io.kotest.core.spec.style.FunSpec
import io.kotest.core.spec.style.funSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import tech.gdragon.db.Database
import tech.gdragon.db.EmbeddedDatabase
import tech.gdragon.db.RemoteDatabase
import tech.gdragon.db.dao.Guild
import tech.gdragon.discord.Command

fun pawaTests(db: Database, isStandalone: Boolean) = funSpec {
  val guildId = 1L

  val pawa: Pawa by lazy {
    db.apply {
      connect()
      migrate()
    }

    transaction(db.database) {
      Guild.findOrCreate(guildId, "Test Guild")
    }

    Pawa(guildId, db, isStandalone)
  }

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
}

class PawaTest : FunSpec({
  val embeddedDatabase: EmbeddedDatabase by lazy {
    val botDataDir = tempdir()
    val url = "jdbc:h2:file:${botDataDir.path}/settings.db"
    EmbeddedDatabase(url)
  }

  val image = "postgres@sha256:b6a3459825427f08ab886545c64d4e5754aa425c5eea678d5359f06a9bf7faab"
  val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse(image))
  val remoteDatabase: RemoteDatabase by lazy {
    postgres.start()
    RemoteDatabase(postgres.jdbcUrl, postgres.username, postgres.password)
  }

  afterSpec {
    postgres.stop()
  }

  include("H2:", pawaTests(embeddedDatabase, isStandalone = true))
  include("Postgres:", pawaTests(remoteDatabase, isStandalone = false))
})
