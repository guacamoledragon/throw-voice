package tech.gdragon.listener

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.Isolate
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.reactivex.subjects.PublishSubject
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
import java.time.Instant
import java.util.concurrent.*
import net.dv8tion.jda.api.entities.Guild as DiscordGuild
import tech.gdragon.db.dao.Guild as GuildDao

/**
 * Tests proving deadlock and blocking bugs in [CombinedAudioRecorderHandler].
 *
 * These tests demonstrate three structural issues:
 *
 * 1. **BUG-1: Caller thread blocks for the entire upload duration** — [saveRecording] holds a
 *    [Semaphore] that is only released after the upload completes inside an RxJava callback on
 *    `Schedulers.io()`. The calling thread (typically JDA's event dispatch) is blocked the whole time.
 *
 * 2. **BUG-3: Permanent deadlock when the RxJava chain is in error state** — If the internal
 *    [PublishSubject] enters an error state, [disconnect]'s subscribe callback receives a `null`
 *    `queueFile`. The lock release is nested inside `queueFile?.let { }` and is never reached,
 *    so the semaphore is never released and the calling thread blocks forever.
 *
 * 3. **Baseline** — Verifies that normal save/disconnect works within a timeout, ensuring the
 *    test infrastructure is valid.
 */
@Isolate
class CombinedAudioRecorderHandlerTest : FunSpec({

  val tempDir = tempdir()
  val guildId = 100_001L
  val voiceChannelId = 200_001L

  lateinit var db: EmbeddedDatabase
  lateinit var mockVoiceChannel: AudioChannel
  lateinit var mockMessageChannel: MessageChannel
  lateinit var mockDatastore: Datastore

  /**
   * Creates a mock [CombinedAudio] frame — 20ms of silence at 48 kHz 16-bit stereo (3840 bytes).
   */
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

  /**
   * Feed [count] audio frames to [recorder], with small delays to let the RxJava `buffer()` operator flush.
   */
  fun feedAudioFrames(recorder: CombinedAudioRecorderHandler, count: Int, userCount: Int = 1) {
    repeat(count) {
      recorder.handleCombinedAudio(createMockCombinedAudio(userCount))
      Thread.sleep(5)
    }
    // Wait for RxJava buffer(200ms) to flush
    Thread.sleep(300)
  }

  beforeSpec {
    File(tempDir, "recordings").mkdirs()

    // In-memory H2 database — note: EmbeddedDatabase.database property is always null
    // due to val initializer capturing _database at construction time; bare transaction {}
    // uses the Exposed default set by connect().
    db = EmbeddedDatabase("carh-test", "mem", "DB_CLOSE_DELAY=-1")
    db.connect()
    db.migrate()

    // Seed guild record (required by CARH constructor's createRecording() transaction)
    transaction {
      GuildDao.findOrCreate(guildId, "Test Guild")
    }

    // Mock JDA types
    val mockGuild = mockk<DiscordGuild>(relaxed = true) {
      every { idLong } returns guildId
      every { id } returns guildId.toString()
      every { name } returns "Test Guild"
    }

    mockVoiceChannel = mockk<AudioChannel>(relaxed = true) {
      every { guild } returns mockGuild
      every { idLong } returns voiceChannelId
      every { id } returns voiceChannelId.toString()
      every { name } returns "test-voice"
    }

    mockMessageChannel = mockk<MessageChannel>(relaxed = true) {
      every { idLong } returns 300_001L
      every { id } returns "300001"
      every { name } returns "test-text"
    }

    mockDatastore = mockk<Datastore>(relaxed = true) {
      every { upload(any(), any()) } returns UploadResult("key", Instant.now(), 100L, "http://localhost/rec.mp3")
    }

    // Guard: stop any leftover Koin from a previous spec
    try { stopKoin() } catch (_: IllegalStateException) { }

    // Start Koin — CARH extends KoinComponent and uses inject() / getProperty()
    startKoin {
      properties(
        mapOf(
          "APP_URL" to "http://localhost",
          "BOT_DATA_DIR" to tempDir.absolutePath,
          "BOT_FILE_FORMAT" to "mp3",
          "BOT_STANDALONE" to "false",
          "BOT_MP3_VBR" to "false",
        )
      )
      modules(module {
        single<Datastore> { mockDatastore }
        single<Pawa> { Pawa(db, PawaConfig { isStandalone = false }) }
        single<Tracer> { OpenTelemetry.noop().getTracer("test") }
      })
    }

    // Mock BotUtils object (Kotlin singleton) to avoid real Discord API calls
    mockkObject(BotUtils)
    every { BotUtils.sendMessage(any(), any<String>()) } just Runs
    every {
      BotUtils.sendMessage(any(), any<net.dv8tion.jda.api.utils.messages.MessageCreateData>())
    } just Runs
    every { BotUtils.uploadFile(any(), any(), any()) } returns null

    // Mock tape utilities — avoid jaudiotagger parsing tiny test MP3s
    mockkStatic("tech.gdragon.api.tape.UtilsKt")
    every { tech.gdragon.api.tape.addCommentToMp3(any(), any()) } just Runs
    // Let queueFileIntoMp3 execute for real so the queue file is properly drained
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
    val recorder = CombinedAudioRecorderHandler(1.0, mockVoiceChannel, mockMessageChannel)
    recorder.session shouldNotBe ""

    feedAudioFrames(recorder, 30)

    val (recording, lock) = recorder.saveRecording(mockVoiceChannel, mockMessageChannel)
    recorder.disconnect(save = true, recording, lock)

    // If we reach here, no deadlock occurred
    recording shouldNotBe null
  }

  // ---------------------------------------------------------------------------
  // BUG-1: saveRecording blocks the caller for the full upload duration
  // ---------------------------------------------------------------------------

  test("BUG-1: saveRecording blocks caller thread for the entire upload duration") {
    // Arrange: upload takes 3 seconds
    every { BotUtils.uploadFile(any(), any(), any()) } answers {
      Thread.sleep(3_000)
      null
    }

    val recorder = CombinedAudioRecorderHandler(1.0, mockVoiceChannel, mockMessageChannel)
    feedAudioFrames(recorder, 30)

    // Act: time how long saveRecording blocks the calling thread
    val start = System.currentTimeMillis()
    val (recording, lock) = recorder.saveRecording(mockVoiceChannel, mockMessageChannel)
    val elapsed = System.currentTimeMillis() - start

    // Assert: the caller was blocked for at least the upload duration.
    // In a healthy design, saveRecording would return immediately and the upload
    // would happen asynchronously.
    elapsed.shouldBeGreaterThan(2_500)

    // Cleanup
    recorder.disconnect(save = true, recording, lock)

    // Restore fast mock for other tests
    every { BotUtils.uploadFile(any(), any(), any()) } returns null
  }

  // ---------------------------------------------------------------------------
  // BUG-3: disconnect deadlocks when the RxJava subject is in error state
  // ---------------------------------------------------------------------------

  test("BUG-3: disconnect deadlocks permanently when RxJava subject has errored").config(
    timeout = kotlin.time.Duration.parse("15s")
  ) {
    val recorder = CombinedAudioRecorderHandler(1.0, mockVoiceChannel, mockMessageChannel)
    feedAudioFrames(recorder, 10)

    // Force the internal PublishSubject into an error state via reflection.
    // In production this can happen when the LAME encoder throws, when the queue
    // file I/O fails in a way that propagates through flatMap, or on unexpected
    // RuntimeExceptions inside doOnNext operators.
    val subjectField = CombinedAudioRecorderHandler::class.java.getDeclaredField("subject")
    subjectField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val subject = subjectField.get(recorder) as PublishSubject<CombinedAudio>
    subject.onError(RuntimeException("simulated chain error"))

    // Give RxJava time to propagate the error to all existing subscribers
    Thread.sleep(500)

    // Create a semaphore with 0 permits, simulating the state after saveRecording
    // acquires the initial permit.
    val lock = Semaphore(1, true)
    lock.acquire() // permits = 0

    // Act: call disconnect on a separate thread — this should deadlock because:
    //  1. disconnect subscribes to `single` which re-derives from the errored subject
    //  2. collectInto errors → Single emits (null, error) to the BiConsumer
    //  3. The callback's lock.release(1) is inside queueFile?.let { } — never reached
    //  4. disconnect's lock.acquire(1) at the end blocks forever
    val future = CompletableFuture.runAsync {
      recorder.disconnect(save = true, null, lock)
    }

    // Assert: the future does NOT complete — proving the permanent deadlock.
    shouldThrow<TimeoutException> {
      future.get(5, TimeUnit.SECONDS)
    }

    // Cleanup: interrupt the blocked thread
    future.cancel(true)
    // Force-dispose to release RxJava resources
    val compositeField = CombinedAudioRecorderHandler::class.java.getDeclaredField("compositeDisposable")
    compositeField.isAccessible = true
    (compositeField.get(recorder) as RecordingDisposable).dispose()
  }
})
