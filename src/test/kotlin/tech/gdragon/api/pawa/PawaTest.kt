package tech.gdragon.api.pawa

import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.db.EmbeddedDatabase
import tech.gdragon.db.dao.Guild
import tech.gdragon.discord.Command

class PawaTest : FunSpec() {
  private lateinit var db: EmbeddedDatabase
  private lateinit var pawa: Pawa

  private val guildId = 1L

  init {
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

  override suspend fun beforeSpec(spec: Spec) {
    db = EmbeddedDatabase("./target")
    pawa = Pawa(1L, db, false)
    transaction(db.database) {
      Guild.findOrCreate(guildId, "Test Guild")
    }
    super.beforeSpec(spec)
  }
}
