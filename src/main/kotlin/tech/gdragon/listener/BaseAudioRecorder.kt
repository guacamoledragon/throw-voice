package tech.gdragon.listener

import com.squareup.tape.QueueFile
import de.sciss.jump3r.lowlevel.LameEncoder
import io.azam.ulidj.ULID
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import net.dv8tion.jda.api.audio.AudioReceiveHandler
import net.dv8tion.jda.api.audio.CombinedAudio
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import org.apache.commons.io.FileUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.api.tape.addCommentToMp3
import tech.gdragon.api.tape.queueFileIntoMp3
import tech.gdragon.api.tape.writeVbrTag
import tech.gdragon.data.Datastore
import tech.gdragon.db.dao.Channel
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.dao.Recording
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Base class containing core audio recording functionality shared by both standalone and shared recorders.
 */
abstract class BaseAudioRecorder(
  override var volume: Double,
  val voiceChannel: AudioChannel,
  val messageChannel: MessageChannel
) : AudioReceiveHandler, KoinComponent, AudioRecorder {

  companion object {
    private const val BITRATE = 128
    private const val AUDIO_QUEUE_CAPACITY = 2000
    private const val BATCH_SIZE = 10 // ~200ms of audio at 50fps, mirrors CARH's buffer(200ms, 8)
  }

  protected val logger = KotlinLogging.logger { }
  protected val datastore: Datastore by inject()
  protected val pawa: Pawa by inject()
  protected val dataDirectory: String by lazy { getKoin().getProperty("BOT_DATA_DIR", "./") }
  protected val fileFormat: String = getKoin().getProperty("BOT_FILE_FORMAT", "mp3").lowercase()
  protected val vbr = getKoin().getProperty<String>("BOT_MP3_VBR").toBoolean()

  // Core recording infrastructure
  protected val audioQueue: BlockingQueue<AudioData> = LinkedBlockingQueue(AUDIO_QUEUE_CAPACITY)
  protected val processingExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
    Thread(r, "audio-processor-$session").apply { isDaemon = true }
  }

  // Recording state
  protected val isRecording = AtomicBoolean(true)
  protected val durationCounter = AtomicLong(0L)
  protected val droppedFrameCount = AtomicLong(0L)
  protected val silencedUsers: MutableSet<Long> = mutableSetOf()

  // Recording session data
  protected var ulid: String = ULID.random()
  protected var recordingRecord: Recording? = null
  protected var queueFile: QueueFile? = null
  protected var lameEncoder: LameEncoder? = null

  override val session: String get() = ulid
  override val recording: Recording? get() = recordingRecord
  val duration: Duration get() = Duration.ofMillis(durationCounter.get() * 20L)

  init {
    initializeRecording()
    startAudioProcessing()
  }

  private fun initializeRecording() {
    try {
      // Create database record
      recordingRecord = transaction {
        Guild.findById(voiceChannel.guild.idLong)?.let { guild ->
          Recording.new(ulid) {
            channel = Channel.findOrCreate(voiceChannel.idLong, voiceChannel.name, voiceChannel.guild.idLong)
            this.guild = guild
          }
        }
      }

      // Initialize queue file
      val queueFilename = "$dataDirectory/recordings/$ulid.queue"
      queueFile = QueueFile(File(queueFilename))

      // Initialize LAME encoder
      lameEncoder = LameEncoder(
        AudioReceiveHandler.OUTPUT_FORMAT,
        BITRATE,
        LameEncoder.CHANNEL_MODE_AUTO,
        LameEncoder.QUALITY_HIGHEST,
        vbr
      )

      logger.info { "Created recording session - $queueFilename" }

    } catch (e: Exception) {
      logger.error(e) { "Failed to initialize recording: $session" }
      cleanup()
    }
  }

  private fun startAudioProcessing() {
    processingExecutor.submit {
      try {
        processAudioLoop()
      } catch (e: Exception) {
        logger.error(e) { "Audio processing failed: $session" }
      }
    }
  }

  private fun processAudioLoop() {
    val mp3Buffer = ByteArray(8192)
    val batchBuffer = mutableListOf<AudioData>()

    while (isRecording.get() || audioQueue.isNotEmpty()) {
      try {
        val audioData = audioQueue.poll(100, TimeUnit.MILLISECONDS)
        if (audioData != null && shouldProcessAudio(audioData)) {
          batchBuffer.add(audioData)
        }

        // Flush batch when full, or when draining remaining frames after recording stops
        val shouldFlush = batchBuffer.size >= BATCH_SIZE
          || (!isRecording.get() && audioQueue.isEmpty() && batchBuffer.isNotEmpty())

        if (shouldFlush) {
          encodeAndWriteBatch(batchBuffer, mp3Buffer)
          batchBuffer.clear()
        }
      } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        break
      } catch (e: Exception) {
        logger.error(e) { "Error processing audio chunk: $session" }
      }
    }

    // Flush any remaining frames that didn't fill a complete batch
    if (batchBuffer.isNotEmpty()) {
      try {
        encodeAndWriteBatch(batchBuffer, mp3Buffer)
      } catch (e: Exception) {
        logger.error(e) { "Error flushing final audio batch: $session" }
      }
    }

    // Flush LAME encoder's internal buffer (up to 1152 samples / ~24ms)
    try {
      val encoder = lameEncoder
      val queue = queueFile
      if (encoder != null && queue != null && fileFormat != "pcm") {
        val flushed = encoder.encodeFinish(mp3Buffer)
        if (flushed > 0) {
          queue.add(mp3Buffer.copyOf(flushed))
          onAudioDataWritten(flushed)
        }
      }
    } catch (e: Exception) {
      logger.warn(e) { "Failed to flush LAME encoder: $session" }
    }

    logger.debug { "Audio processing loop ended for session: $session" }
  }

  private fun encodeAndWriteBatch(batch: List<AudioData>, mp3Buffer: ByteArray) {
    val encoder = lameEncoder ?: return
    val queue = queueFile ?: return

    try {
      val baos = ByteArrayOutputStream()

      for (audioData in batch) {
        if (fileFormat == "pcm") {
          baos.write(audioData.data)
        } else {
          val bytesEncoded = encoder.encodeBuffer(audioData.data, 0, audioData.data.size, mp3Buffer)
          if (bytesEncoded > 0) {
            baos.write(mp3Buffer, 0, bytesEncoded)
          }
        }
      }

      val dataToWrite = baos.toByteArray()
      if (dataToWrite.isNotEmpty()) {
        // Apply size management policy
        handleSizeLimit(queue, dataToWrite)

        // Write to queue file — single write for the entire batch
        queue.add(dataToWrite)
        onAudioDataWritten(dataToWrite.size)

        logger.debug { "Encoded batch of ${batch.size} frames (${dataToWrite.size} bytes) for session: $session" }
      }

    } catch (e: IOException) {
      logger.warn(e) { "Failed to write audio batch: $session" }
    }
  }

  override fun saveRecording(
    voiceChannel: AudioChannel,
    messageChannel: MessageChannel
  ): Pair<Recording?, Semaphore> {
    val saveStartMs = System.currentTimeMillis()
    logger.info { "[QUEUE] saveRecording started: $session, duration=$duration, impl=QUEUE" }
    isRecording.set(false)
    val recordingLock = Semaphore(1, true)
    recordingLock.acquire()

    logger.debug { "Stopping recording and processing remaining audio: $session" }

    // Wait for processing to complete
    processingExecutor.shutdown()
    try {
      processingExecutor.awaitTermination(10, TimeUnit.SECONDS)
    } catch (e: InterruptedException) {
      logger.warn { "Audio processing didn't complete in time: $session" }
    }

    val saveElapsedMs = System.currentTimeMillis() - saveStartMs
    logger.info { "[QUEUE] saveRecording completed (upload in background): $session, elapsed=${saveElapsedMs}ms, impl=QUEUE" }

    // Process the recording in a background thread
    thread {
      try {
        processCompletedRecording(voiceChannel, messageChannel)
      } finally {
        recordingLock.release()
      }
    }

    return Pair(recordingRecord, recordingLock)
  }

  private fun processCompletedRecording(voiceChannel: AudioChannel, messageChannel: MessageChannel) {
    val queue = queueFile ?: return

    withLoggingContext(
      "guild" to voiceChannel.guild.name,
      "guild.id" to voiceChannel.guild.id,
      "text-channel" to messageChannel.name,
      "session-id" to session,
      "audio.frames.dropped" to droppedFrameCount.get().toString(),
      "audio.queue.depth" to audioQueue.size.toString()
    ) {
      try {
        logger.info { "Processing completed recording: $session, queue size: ${queue.size()}" }

        val recordingFile = File("$dataDirectory/recordings/$ulid.$fileFormat")
        queueFileIntoMp3(queue, recordingFile)

        if (vbr && fileFormat == "mp3") {
          lameEncoder?.let { writeVbrTag(it, recordingFile) }
        }

        addCommentToMp3(recordingFile, recording?.speakers?.joinToString(prefix = "Speakers: ") { it.name })

        logger.info {
          "Generated audio file ${recordingFile.name} - ${FileUtils.byteCountToDisplaySize(recordingFile.length())}"
        }

        if (recordingFile.length() <= 0) {
          handleEmptyRecording(messageChannel)
        } else {
          uploadRecording(recordingFile, voiceChannel, messageChannel)
        }

      } catch (e: Exception) {
        logger.error(e) { "Failed to process completed recording: $session" }
        handleRecordingError(messageChannel)
      }
    }
  }

  private fun handleEmptyRecording(channel: MessageChannel) {
    // Same for both types
    tech.gdragon.BotUtils.sendMessage(channel, ":no_entry_sign: _Recording is empty, not uploading._")
    transaction { recordingRecord?.delete() }
  }

  private fun handleRecordingError(channel: MessageChannel) {
    // Same for both types
    val errorMessage = """
        |:no_entry_sign: _Error creating recording, please visit support server and provide Session ID._
        |_Session ID: `$session`_
        |""".trimMargin()
    tech.gdragon.BotUtils.sendMessage(channel, errorMessage)
  }

  fun disconnect(recordingLock: Semaphore? = null) {
    isRecording.set(false)
    recordingLock?.let { lock ->
      try {
        lock.acquire()
        lock.release()
      } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
      }
    }
    cleanup()
    recordingLock?.release()
  }

  private fun cleanup() {
    try {
      if (!processingExecutor.isShutdown) {
        processingExecutor.shutdown()
      }
      audioQueue.clear()
      if (!processingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        processingExecutor.shutdownNow()
        processingExecutor.awaitTermination(2, TimeUnit.SECONDS)
      }
      queueFile?.close()
      lameEncoder?.close()
      logger.info { "Cleanup completed for session: $session" }
    } catch (e: Exception) {
      logger.error(e) { "Error during cleanup: $session" }
    }
  }

  override fun disconnect(save: Boolean, recording: Recording?, recordingLock: Semaphore?) {
    val disconnectStartMs = System.currentTimeMillis()
    logger.info { "[QUEUE] disconnect started: $session, save=$save, impl=QUEUE" }
    disconnect(recordingLock)
    val disconnectElapsedMs = System.currentTimeMillis() - disconnectStartMs
    logger.info { "[QUEUE] disconnect completed: $session, elapsed=${disconnectElapsedMs}ms, impl=QUEUE" }
  }

  override fun silenceUser(userId: Long) { silencedUsers.add(userId) }

  fun uploadAttachment(messageChannel: MessageChannel, recordingFile: File, filename: String): Message? {
    return if (recordingFile.length() < Message.MAX_FILE_SIZE)
      BotUtils.uploadFile(messageChannel, recordingFile, filename)
    else null
  }

  // AudioReceiveHandler implementation
  override fun canReceiveUser(): Boolean = false
  override fun canReceiveCombined(): Boolean = isRecording.get()

  override fun handleCombinedAudio(combinedAudio: CombinedAudio) {
    if (!isRecording.get()) return

    try {
      durationCounter.incrementAndGet()
      recording?.speakers?.addAll(combinedAudio.users)

      val audioData = combinedAudio.getAudioData(volume)
      if (audioData.isNotEmpty()) {
        val data = AudioData(audioData, combinedAudio.users.size)

        if (!audioQueue.offer(data)) {
          audioQueue.poll()
          audioQueue.offer(data)
          droppedFrameCount.incrementAndGet()
        }
      }

    } catch (e: Exception) {
      logger.error(e) { "Error handling combined audio: $session" }
    }
  }

  override fun includeUserInCombinedAudio(user: User): Boolean {
    return !silencedUsers.contains(user.idLong)
  }

  // Abstract methods for subclass-specific behavior
  protected abstract fun shouldProcessAudio(audioData: AudioData): Boolean
  protected abstract fun handleSizeLimit(queue: QueueFile, dataToWrite: ByteArray)
  protected abstract fun onAudioDataWritten(bytesWritten: Int)
  protected abstract fun uploadRecording(
    recordingFile: File,
    voiceChannel: AudioChannel,
    messageChannel: MessageChannel
  )

  // Shared data class
  protected data class AudioData(
    val data: ByteArray,
    val userCount: Int
  ) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false
      other as AudioData
      return data.contentEquals(other.data) && userCount == other.userCount
    }

    override fun hashCode(): Int {
      var result = data.contentHashCode()
      result = 31 * result + userCount
      return result
    }
  }
}
