package tech.gdragon.listener

import de.sciss.jump3r.lowlevel.LameEncoder
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
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
import java.nio.ByteBuffer
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

  val pcmChannel: FileChannel? = RandomAccessFile("recordings/$uuid.pcm", "rw").channel
  private val logger = LoggerFactory.getLogger(this.javaClass)

  val subject = PublishSubject.create<CombinedAudio>()
  private var subscription: Disposable? = null

  var canReceive = true
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
          println("bytesEncoded = ${bytesEncoded}")
          baos.write(buffer, 0, bytesEncoded)
        }

        Observable.fromArray(baos.toByteArray())
      })
      .collectInto(pcmChannel, { channel, bytes ->
        val inputByteChannel = Channels.newChannel(ByteArrayInputStream(bytes))

        channel?.apply {
          val size = transferFrom(inputByteChannel, position(), bytes.size.toLong())
          position(position() + size)
        }
      })
      .subscribe()
  }

  override fun canReceiveUser(): Boolean = false

  override fun handleCombinedAudio(combinedAudio: CombinedAudio) {
    if (!isAfk(combinedAudio.users.size)) {
      subject.onNext(combinedAudio)
      /*val audioData = combinedAudio.getAudioData(volume)
      val inputByteChannel = Channels.newChannel(ByteArrayInputStream(audioData))

      pcmChannel?.apply {
        val size = transferFrom(inputByteChannel, position(), audioData.size.toLong())
        position(position() + size)
      }*/
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
        disconnect()
        BotUtils.leaveVoiceChannel(voiceChannel)
      }
    }

    return isAfk
  }

  fun getVoiceData(): Single<ByteArrayOutputStream>? {
    canReceive = false
    return Observable.create<ByteBuffer> { emitter ->
      val buffer = ByteBuffer.allocate(BYTE_SAMPLE_SIZE)

      pcmChannel?.let {
        while (it.read(buffer) > 0) {
          emitter.onNext(buffer.asReadOnlyBuffer().flip())
          buffer.clear()
        }
        canReceive = true
        emitter.onComplete()
      }
    }.map {
      val bytes = ByteArray(it.capacity())
      it.get(bytes)
      bytes
    }.reduce(ByteArrayOutputStream(BYTE_SAMPLE_SIZE)) { baos, bytes ->
      baos.write(bytes)
      baos
    }
  }

  fun disconnect() {
    canReceive = false
    subject.onComplete()
    subscription?.dispose()
    pcmChannel?.close()
  }
}
