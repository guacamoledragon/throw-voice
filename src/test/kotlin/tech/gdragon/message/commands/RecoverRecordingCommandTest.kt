package tech.gdragon.message.commands

import io.azam.ulidj.ULID
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.mockk.clearMocks
import io.mockk.mockk
import tech.gdragon.api.commands.RecoverResult
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.api.pawa.PawaConfig
import tech.gdragon.data.Datastore
import tech.gdragon.data.LocalDatastore
import tech.gdragon.db.Database
import tech.gdragon.db.dao.Recording

class RecoverRecordingCommandTest : FunSpec({

  // Create real instances to avoid MockK issues with class mocking on Java 25
  val db: Database = mockk(relaxed = true)
  val config = PawaConfig { dataDirectory = "/tmp" }
  val datastore: Datastore = mockk<LocalDatastore>(relaxed = true)
  val recording: Recording = mockk<Recording>(relaxed = true)

  test("returns valid recovered results when message has a valid Session ID") {
    val sessionId = ULID.random()
    // Create a test-specific subclass that overrides the method
    val pawa = object : Pawa(db, config) {
      override fun recoverRecording(datastore: Datastore, sessionId: String) =
        RecoverResult(sessionId, recording, null)
    }

    val message = """
        $sessionId
      """.trimIndent()

    val sut = RecoverRecordingCommand(pawa, datastore, message)

    sut.failedRecordings().shouldBeEmpty()
    sut.successfulRecordings().shouldNotBeEmpty()
    sut.toString().shouldBe(":white_check_mark: `$sessionId`")
  }

  test("returns valid failed results when message has unrecoverable valid Session ID") {
    val sessionId = ULID.random()
    val pawa = object : Pawa(db, config) {
      override fun recoverRecording(datastore: Datastore, sessionId: String) =
        RecoverResult(sessionId, null)
    }

    val message = """
        $sessionId
      """.trimIndent()

    val sut = RecoverRecordingCommand(pawa, datastore, message)

    sut.successfulRecordings().shouldBeEmpty()
    sut.failedRecordings().shouldNotBeEmpty()
    sut.toString().shouldBe(":x: `$sessionId` __")
  }

  test("returns empty when message has invalid Session ID") {
    val sessionId = "invalid-session-id"
    val pawa = object : Pawa(db, config) {
      override fun recoverRecording(datastore: Datastore, sessionId: String) =
        RecoverResult(sessionId, null)
    }

    val message = """
        $sessionId
      """.trimIndent()

    val sut = RecoverRecordingCommand(pawa, datastore, message)

    sut.failedRecordings().shouldBeEmpty()
    sut.successfulRecordings().shouldBeEmpty()
    sut.toString().shouldBeEmpty()
  }

  afterTest {
    clearMocks(datastore, recording, answers = false, recordedCalls = true, verificationMarks = true)
  }
})
