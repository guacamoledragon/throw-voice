package tech.gdragon.db

import io.kotest.core.spec.style.FunSpec
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.flywaydb.core.api.output.MigrateOutput
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

class DatabaseTest : FunSpec({
  val embedded = EmbeddedDatabase("jdbc:h2:mem:settings;DB_CLOSE_DELAY=-1")

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
})

fun migrationTests(db: Database) = funSpec {
  test("flyway migration") {
    db.connect()
    val result = db.migrate()

    result.success shouldBe true
    result.migrations shouldNotBe emptyList<MigrateOutput>()
  }
}
