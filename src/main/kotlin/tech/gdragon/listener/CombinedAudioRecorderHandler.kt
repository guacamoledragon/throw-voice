package tech.gdragon.listener

import com.squareup.tape.QueueFile
import de.sciss.jump3r.lowlevel.LameEncoder
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import net.dv8tion.jda.core.audio.AudioReceiveHandler
import net.dv8tion.jda.core.audio.CombinedAudio
import net.dv8tion.jda.core.audio.UserAudio
import net.dv8tion.jda.core.entities.VoiceChannel
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import tech.gdragon.BotUtils
import tech.gdragon.db.dao.Guild
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class CombinedAudioRecorderHandler(val volume: Double, val voiceChannel: VoiceChannel) : AudioReceiveHandler {
  companion object {
    private const val AFK_LIMIT = (2 * 60 * 1000) / 20      // 2 minutes in ms over 20ms increments
    private const val MAX_RECORDING_SIZE =  1 * 1024 * 1024 // 8MB
    private const val BUFFER_TIMEOUT = 200L                 // 200 milliseconds
    private const val BUFFER_MAX_COUNT = 8
  }

  private val logger = LoggerFactory.getLogger(this.javaClass)

  // State-licious
  private var subject: Subject<CombinedAudio>? = null
  private var subscription: Disposable? = null
  private var uuid: UUID? = null

  private var canReceive = true
  private var afkCounter = 0

  var mp3Filename: String? = null

  init {
    subscription = createRecording()
  }

  override fun canReceiveUser(): Boolean = false

  override fun handleCombinedAudio(combinedAudio: CombinedAudio) {
    if (!isAfk(combinedAudio.users.size)) {
      subject?.onNext(combinedAudio)
    }
  }

  override fun handleUserAudio(userAudio: UserAudio?) = TODO("Not implemented.")

  override fun canReceiveCombined(): Boolean = canReceive

  /**
   * Checks if everyone in voice chat is afk. Super malformed function as it has
   * side effects and triggers messages outside of the scope
   */
  private fun isAfk(userCount: Int): Boolean {
    if (userCount == 0) afkCounter++ else afkCounter = 0

    val isAfk = afkCounter >= AFK_LIMIT

    if (isAfk) {
      logger.info("AFK detected, leaving '{}' voice channel in {}", voiceChannel.name, voiceChannel.guild.name)
      transaction {
        Guild.findById(voiceChannel.guild.idLong)?.settings?.defaultTextChannel
      }?.let {
        val textChannel = voiceChannel.guild.getTextChannelById(it)
        BotUtils.sendMessage(textChannel, "No audio for 2 minutes, leaving from AFK detection...")
      }

      thread(start = true) {
        BotUtils.leaveVoiceChannel(voiceChannel)
      }
    }

    return isAfk
  }

  private fun createRecording(): Disposable? {
    subject = PublishSubject.create()
    uuid = UUID.randomUUID()
    mp3Filename = "recordings/$uuid.mp3"

    var recordingSize = 0

    val encoder = LameEncoder(AudioReceiveHandler.OUTPUT_FORMAT, 128, LameEncoder.CHANNEL_MODE_AUTO, LameEncoder.QUALITY_HIGHEST, false)

    return subject
      ?.map { it.getAudioData(volume) }
      ?.buffer(BUFFER_TIMEOUT, TimeUnit.MILLISECONDS, BUFFER_MAX_COUNT)
      ?.flatMap({ bytesArray ->
        val baos = ByteArrayOutputStream()

        bytesArray.forEach {
          val buffer = ByteArray(it.size)
          val bytesEncoded = encoder.encodeBuffer(it, 0, it.size, buffer)
          baos.write(buffer, 0, bytesEncoded)
        }

        Observable.fromArray(baos.toByteArray())
      })
      ?.collectInto(QueueFile(File("recordings/$uuid.queue")), { queue, bytes ->

        while (recordingSize + bytes.size > MAX_RECORDING_SIZE) {
          recordingSize -= queue.peek()?.size ?: 0
          queue.remove()
        }

        queue.add(bytes)
        recordingSize += bytes.size
      })
      ?.subscribe({ queue ->
        val outputStream = FileOutputStream(File(mp3Filename))
        queue.forEach({ stream, _ -> stream.transferTo(outputStream) })
        queue.close()
        outputStream.close()
        File("recordings/$uuid.queue").delete()

        println("queue.size() = ${queue.size()}")
      })
  }

  fun disconnect() {
    canReceive = false
    subject?.onComplete()
    subscription?.dispose()
  }
}
