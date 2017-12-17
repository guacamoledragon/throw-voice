package tech.gdragon.listener

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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.RandomAccessFile
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class CombinedAudioRecorderHandler(val volume: Double, val voiceChannel: VoiceChannel, val uuid: UUID = UUID.randomUUID()) : AudioReceiveHandler {
  companion object {
    private const val BYTE_SAMPLE_SIZE = 3840 // Size of one 20 ms sample
    private const val AFK_LIMIT = (2 * 60 * 1000) / 20 // 2 mins in ms over 20ms increments
  }

  private val logger = LoggerFactory.getLogger(this.javaClass)

  val mp3Filename = "recordings/$uuid.mp3"
  val mp3Channel: FileChannel? = RandomAccessFile(mp3Filename, "rw").channel

  private val subject: Subject<CombinedAudio> = PublishSubject.create()
  private var subscription: Disposable? = null

  private var canReceive = true
  private var afkCounter = 0

  init {
    val encoder = LameEncoder(AudioReceiveHandler.OUTPUT_FORMAT, 128, LameEncoder.CHANNEL_MODE_AUTO, LameEncoder.QUALITY_HIGHEST, false)

    subscription = subject
      .map { it.getAudioData(volume) }
      .buffer(200, TimeUnit.MILLISECONDS, 8)
      .flatMap({ bytesArray ->
        val baos = ByteArrayOutputStream()

        bytesArray.forEach {
          val buffer = ByteArray(it.size)
          val bytesEncoded = encoder.encodeBuffer(it, 0, it.size, buffer)
          baos.write(buffer, 0, bytesEncoded)
        }

        Observable.fromArray(baos.toByteArray())
      })
      .collectInto(mp3Channel, { channel, bytes ->
        val inputByteChannel = Channels.newChannel(ByteArrayInputStream(bytes))

        channel?.apply {
          val size = transferFrom(inputByteChannel, position(), bytes.size.toLong())
          position(position() + size)
        }
      })
      .map { println("it.size() = ${it.size()}") }
      .subscribe()
  }

  override fun canReceiveUser(): Boolean = false

  override fun handleCombinedAudio(combinedAudio: CombinedAudio) {
    if (!isAfk(combinedAudio.users.size)) {
      subject.onNext(combinedAudio)
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

  fun disconnect() {
    canReceive = false
    subject.onComplete()
    subscription?.dispose()
    mp3Channel?.close()
  }
}