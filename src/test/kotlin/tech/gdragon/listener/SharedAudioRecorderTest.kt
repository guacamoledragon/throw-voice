package tech.gdragon.listener

import io.kotest.core.annotation.Isolate
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import net.dv8tion.jda.api.audio.CombinedAudio
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.api.pawa.PawaConfig
import tech.gdragon.data.Datastore
import tech.gdragon.data.UploadResult
import tech.gdragon.db.EmbeddedDatabase
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import net.dv8tion.jda.api.entities.Guild as DiscordGuild
import tech.gdragon.db.dao.Guild as GuildDao

/**
 * Tests for [SharedAudioRecorder] (the [BaseAudioRecorder] subclass for non-standalone mode).
 *
 * These tests demonstrate that the newer BlockingQueue-based architecture avoids the
 * deadlock and blocking issues present in [CombinedAudioRecorderHandler]:
 *
 * 1. **saveRecording returns quickly** — the upload runs in a background thread, so the
 *    calling thread is not held during I/O.
 *
 * 2. **Error resilience** — even when the upload throws, the `finally` block in the upload
 *    thread always releases the semaphore, so [disconnect] never deadlocks.
 *
 * 3. **Baseline** — Normal lifecycle completes within timeout.
 */
@Isolate
class SharedAudioRecorderTest : FunSpec({

  val tempDir = tempdir()
  val guildId = 100_002L
  val voiceChannelId = 200_002L

  lateinit var db: EmbeddedDatabase
  lateinit var mockVoiceChannel: AudioChannel
  lateinit var mockMessageChannel: MessageChannel
  lateinit var mockDatastore: Datastore

  fun createMockCombinedAudio(userCount: Int = 1): CombinedAudio {
    val audio = mockk<CombinedAudio>()
    val users = (1..userCount).map {
      mockk<User>(relaxed = true) {
        every { idLong } returns it.toLong()
      }
    }
    every { audio.users } returns users
    every { audio.getAudioData(any()) } returns ByteArray(3840)
    return audio
  }

  fun feedAudioFrames(recorder: BaseAudioRecorder, count: Int, userCount: Int = 1) {
    repeat(count) {
      recorder.handleCombinedAudio(createMockCombinedAudio(userCount))
      Thread.sleep(5)
    }
    // Let the single-threaded processor drain the queue
    Thread.sleep(300)
  }

  beforeSpec {
    File(tempDir, "recordings").mkdirs()

    db = EmbeddedDatabase("bar-test", "mem", "DB_CLOSE_DELAY=-1")
    db.connect()
    db.migrate()

    transaction {
      GuildDao.findOrCreate(guildId, "Test Guild BAR")
    }

    val mockGuild = mockk<DiscordGuild>(relaxed = true) {
      every { idLong } returns guildId
      every { id } returns guildId.toString()
      every { name } returns "Test Guild BAR"
    }

    mockVoiceChannel = mockk<AudioChannel>(relaxed = true) {
      every { guild } returns mockGuild
      every { idLong } returns voiceChannelId
      every { id } returns voiceChannelId.toString()
      every { name } returns "test-voice"
    }

    mockMessageChannel = mockk<MessageChannel>(relaxed = true) {
      every { idLong } returns 300_002L
      every { id } returns "300002"
      every { name } returns "test-text"
    }

    mockDatastore = mockk<Datastore>(relaxed = true) {
      every { upload(any(), any()) } returns UploadResult("key", Instant.now(), 100L, "http://localhost/rec.mp3")
    }

    // Guard: stop any leftover Koin from a previous spec
    try { stopKoin() } catch (_: IllegalStateException) { }

    startKoin {
      properties(
        mapOf(
          "APP_URL" to "http://localhost",
          "BOT_DATA_DIR" to tempDir.absolutePath,
          "BOT_FILE_FORMAT" to "mp3",
          "BOT_RECORDER_TYPE" to "QUEUE",
          "BOT_STANDALONE" to "false",
          "BOT_MP3_VBR" to "false",
        )
      )
      modules(module {
        single<Datastore> { mockDatastore }
        single<Pawa> { Pawa(db, PawaConfig { isStandalone = false; recorderType = tech.gdragon.api.pawa.RecorderType.QUEUE }) }
      })
    }

    mockkObject(BotUtils)
    every { BotUtils.sendMessage(any(), any<String>()) } just Runs
    every {
      BotUtils.sendMessage(any(), any<net.dv8tion.jda.api.utils.messages.MessageCreateData>())
    } just Runs
    every { BotUtils.uploadFile(any(), any(), any()) } returns null

    mockkStatic("tech.gdragon.api.tape.UtilsKt")
    every { tech.gdragon.api.tape.addCommentToMp3(any(), any()) } just Runs
    every { tech.gdragon.api.tape.queueFileIntoMp3(any<com.squareup.tape.QueueFile>(), any()) } answers { callOriginal() }
  }

  afterSpec {
    unmockkObject(BotUtils)
    unmockkStatic("tech.gdragon.api.tape.UtilsKt")
    stopKoin()
    db.shutdown()
  }

  // ---------------------------------------------------------------------------
  // Baseline
  // ---------------------------------------------------------------------------

  test("save and disconnect completes normally within timeout").config(
    timeout = kotlin.time.Duration.parse("15s")
  ) {
    val recorder = SharedAudioRecorder(1.0, mockVoiceChannel, mockMessageChannel)
    recorder.session shouldNotBe ""

    feedAudioFrames(recorder, 30)

    val (recording, lock) = recorder.saveRecording(mockVoiceChannel, mockMessageChannel)
    recorder.disconnect(lock)

    recording shouldNotBe null
  }

  // ---------------------------------------------------------------------------
  // Contrast with BUG-1: saveRecording does NOT block the caller during upload
  // ---------------------------------------------------------------------------

  test("saveRecording returns quickly even when upload is slow") {
    // Arrange: upload takes 3 seconds
    every { BotUtils.uploadFile(any(), any(), any()) } answers {
      Thread.sleep(3_000)
      null
    }

    val recorder = SharedAudioRecorder(1.0, mockVoiceChannel, mockMessageChannel)
    feedAudioFrames(recorder, 30)

    // Act: time how long saveRecording takes
    val start = System.currentTimeMillis()
    val (_, lock) = recorder.saveRecording(mockVoiceChannel, mockMessageChannel)
    val saveElapsed = System.currentTimeMillis() - start

    // Assert: saveRecording returns quickly — upload happens in a background thread.
    // The executor shutdown + awaitTermination adds some overhead, but it should be
    // well under the 3 second upload time.
    saveElapsed.shouldBeLessThan(2_000)

    // Disconnect blocks until the background upload thread finishes
    val disconnectStart = System.currentTimeMillis()
    recorder.disconnect(lock)
    val disconnectElapsed = System.currentTimeMillis() - disconnectStart

    // The upload latency shows up here in disconnect, NOT in saveRecording
    disconnectElapsed.shouldBeGreaterThan(500)

    // Restore fast mock
    every { BotUtils.uploadFile(any(), any(), any()) } returns null
  }

  // ---------------------------------------------------------------------------
  // Contrast with BUG-3: disconnect never deadlocks even when upload errors
  // ---------------------------------------------------------------------------

  test("disconnect completes even when upload throws an exception").config(
    timeout = kotlin.time.Duration.parse("15s")
  ) {
    // Arrange: upload throws a RuntimeException (simulating S3/Discord failure)
    every { BotUtils.uploadFile(any(), any(), any()) } throws RuntimeException("upload failed")

    val recorder = SharedAudioRecorder(1.0, mockVoiceChannel, mockMessageChannel)
    feedAudioFrames(recorder, 30)

    val (_, lock) = recorder.saveRecording(mockVoiceChannel, mockMessageChannel)

    // Act + Assert: disconnect should complete despite the upload error.
    // In BaseAudioRecorder, the upload thread's finally block ALWAYS releases the
    // semaphore regardless of what exception is thrown.
    val future = CompletableFuture.runAsync {
      recorder.disconnect(lock)
    }

    // Should complete within 5 seconds — no deadlock
    future.get(5, TimeUnit.SECONDS)

    // Restore mock
    every { BotUtils.uploadFile(any(), any(), any()) } returns null
  }

  // ---------------------------------------------------------------------------
  // Contrast with BUG-3: disconnect completes even when IOException occurs
  // ---------------------------------------------------------------------------

  test("disconnect completes even when queueFileIntoMp3 throws IOException").config(
    timeout = kotlin.time.Duration.parse("15s")
  ) {
    // Arrange: simulate corrupt queue file
    every {
      tech.gdragon.api.tape.queueFileIntoMp3(any<com.squareup.tape.QueueFile>(), any())
    } throws IOException("corrupt queue file")

    val recorder = SharedAudioRecorder(1.0, mockVoiceChannel, mockMessageChannel)
    feedAudioFrames(recorder, 30)

    val (_, lock) = recorder.saveRecording(mockVoiceChannel, mockMessageChannel)

    // Act + Assert: disconnect completes — the finally block releases the lock
    val future = CompletableFuture.runAsync {
      recorder.disconnect(lock)
    }
    future.get(5, TimeUnit.SECONDS)

    // Restore mock
    every {
      tech.gdragon.api.tape.queueFileIntoMp3(any<com.squareup.tape.QueueFile>(), any())
    } answers { callOriginal() }
  }
})
