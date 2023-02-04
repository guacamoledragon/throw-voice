package tech.gdragon.db

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.flywaydb.core.api.output.MigrateOutput

class EmbeddedDatabaseTest : FunSpec({
  val database = EmbeddedDatabase("jdbc:h2:mem:settings;DB_CLOSE_DELAY=-1")
  beforeSpec {
    database.connect()
  }

  afterSpec {
    database.shutdown()
  }

  test("flyway migration") {
    val result = database.migrate()

    result.success shouldBe true
    result.migrations shouldNotBe emptyList<MigrateOutput>()
  }
})
