package tech.gdragon.db

import io.kotest.core.spec.style.FunSpec
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.flywaydb.core.api.output.MigrateOutput
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

class DatabaseTest : FunSpec({
  val embedded = EmbeddedDatabase("settings", "mem", "DB_CLOSE_DELAY=-1")

  val image = "postgres@sha256:b6a3459825427f08ab886545c64d4e5754aa425c5eea678d5359f06a9bf7faab"
  val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse(image))
  val remote: RemoteDatabase by lazy {
    postgres.start()
    RemoteDatabase(postgres.jdbcUrl, postgres.username, postgres.password)
  }

  afterSpec {
    remote.shutdown()
    embedded.shutdown()
    postgres.stop()
  }

  include("H2:", migrationTests(embedded))
  include("Postgres:", migrationTests(remote))

  // Compatibility guard: an embedded database created by H2 2.2.224 (where V1 was applied with the
  // old DATETIME-based checksum) must still open and migrate under the current H2 version. This
  // exercises the Flyway repair() that realigns V1's checksum after DATETIME -> TIMESTAMP.
  test("migrates a pre-existing H2 2.2.224 database") {
    val tmpDir = createTempDirectory("h2-compat")
    val fixture = checkNotNull(javaClass.getResourceAsStream("/h2-2.2.224-settings.db.mv.db")) {
      "missing 2.2.224 fixture database"
    }
    fixture.use { Files.copy(it, tmpDir.resolve("settings.db.mv.db")) }

    val legacy = EmbeddedDatabase(tmpDir.resolve("settings.db").toString(), "file")
    try {
      legacy.connect()
      val result = legacy.migrate()
      result.success shouldBe true
    } finally {
      legacy.shutdown()
    }
  }
})

fun migrationTests(db: Database) = funSpec {
  test("flyway migration") {
    db.connect()
    val result = db.migrate()

    result.success shouldBe true
    result.migrations shouldNotBe emptyList<MigrateOutput>()
  }
}
