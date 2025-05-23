package tech.gdragon.api.pawa

import com.squareup.tape.QueueFile
import io.azam.ulidj.ULID
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.spec.style.funSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jetbrains.exposed.sql.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import tech.gdragon.data.Datastore
import tech.gdragon.data.LocalDatastore
import tech.gdragon.db.Database
import tech.gdragon.db.EmbeddedDatabase
import tech.gdragon.db.RemoteDatabase
import tech.gdragon.db.dao.Channel
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.dao.Recording
import tech.gdragon.db.now
import tech.gdragon.discord.Command
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

fun pawaTests(db: Database, ds: Datastore, isStandalone: Boolean) = funSpec {
  val guildId = 1L

  val pawa: Pawa by lazy {
    db.apply {
      connect()
      migrate()
    }

    transaction(db.database) {
      Guild.findOrCreate(guildId, "Test Guild")
    }

    val config = PawaConfig {
      this.isStandalone = isStandalone
      this.dataDirectory = tempdir().path
    }

    Pawa(db, config)
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

  context("when recover") {
    test("it should return null when Session ID doesn't exist") {
      val result = pawa.recoverRecording(ds, "fake-session-id")

      result.recording.shouldBeNull()
    }

    test("it should return record when it exists even when queue and mp3 files are missing") {
      val record = transaction(db.database) {
        val guild = Guild.findOrCreate(guildId, "Test Guild")
        val channel = Channel.findOrCreate(1L, "fake-voice-channel", guildId)

        Recording.new(ULID.random()) {
          this.channel = channel
          this.guild = guild
        }
      }
      val result = pawa.recoverRecording(ds, record.id.value)

      record.shouldNotBeNull()
      result.recording.shouldNotBeNull()
    }

    test("it should return existing Recording if MP3 exists") {
      val dataDirectory = pawa.config.dataDirectory
      val sessionId = ULID.random()

      // Create mp3 file
      Files.createDirectories(File(dataDirectory, "recordings").toPath())
      FileOutputStream(File(dataDirectory, "recordings/$sessionId.mp3")).close()

      val record = transaction(db.database) {
        val guild = Guild.findOrCreate(guildId, "Test Guild")
        val channel = Channel.findOrCreate(1L, "fake-voice-channel", guildId)

        Recording.new(sessionId) {
          this.channel = channel
          this.guild = guild
          size = 1024
          modifiedOn = now().plusSeconds(60L)
          url = "https://fake-link.com"
        }
      }
      val result = pawa.recoverRecording(ds, record.id.value)

      result.recording.shouldNotBeNull()
      result.recording.id.shouldBe(record.id)
      result.recording.url.shouldNotBe(record.url)
    }

    test("it should return recovered Recording when URL does not exist") {
      val dataDirectory = pawa.config.dataDirectory
      val sessionId = ULID.random()

      // Create queue file
      Files.createDirectories(File(dataDirectory, "recordings").toPath())
      QueueFile(File(dataDirectory, "recordings/$sessionId.queue"))

      val record = transaction(db.database) {
        val guild = Guild.findOrCreate(guildId, "Test Guild")
        val channel = Channel.findOrCreate(1L, "fake-voice-channel", guildId)

        Recording.new(sessionId) {
          this.channel = channel
          this.guild = guild
        }
      }
      val result = pawa.recoverRecording(ds, record.id.value)

      result.recording.shouldNotBeNull()
      result.recording.id.shouldBe(record.id)
      result.recording.url.shouldNotBe(record.url)
    }
  }
}

class PawaTest : FunSpec({
  val embeddedDatabase: EmbeddedDatabase by lazy {
    val botDataDir = tempdir()
    val url = "${botDataDir.path}/settings.db"
    EmbeddedDatabase(url)
  }

  val image = "postgres@sha256:b6a3459825427f08ab886545c64d4e5754aa425c5eea678d5359f06a9bf7faab"
  val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse(image))
  val remoteDatabase: RemoteDatabase by lazy {
    postgres.start()
    RemoteDatabase(postgres.jdbcUrl, postgres.username, postgres.password)
  }
  val localDatastore: Datastore = LocalDatastore("./data-store")

  afterSpec {
    postgres.stop()
  }

  include("H2:", pawaTests(embeddedDatabase, localDatastore, isStandalone = true))
  include("Postgres:", pawaTests(remoteDatabase, localDatastore, isStandalone = false))
})
