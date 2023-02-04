package tech.gdragon.db

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateOutput
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import javax.sql.DataSource

class RemoteDatabaseTest : FunSpec({

  val image = "postgres@sha256:b6a3459825427f08ab886545c64d4e5754aa425c5eea678d5359f06a9bf7faab"
  val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse(image))

  val dataSource: DataSource by lazy {
    postgres.jdbcUrl.let { url ->
      DriverManager
        .getConnection(url, postgres.username, postgres.password)
        .let { _ ->
          PGSimpleDataSource().apply {
            setUrl(url)
            user = postgres.username
            password = postgres.password
          }
        }
    }
  }

  beforeTest {
    postgres.start()
  }

  afterTest {
    postgres.stop()
  }

  test("test flyway migration") {
    val flyway = Flyway.configure()
      .dataSource(dataSource)
      .baselineOnMigrate(true)
      .locations("filesystem:./sql/common", "filesystem:./sql/postgres")
      .load()

    val result = flyway.migrate()

    result.success shouldBe true
    result.migrations shouldNotBe emptyList<MigrateOutput>()
  }
})
