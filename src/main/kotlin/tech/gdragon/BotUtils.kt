package tech.gdragon

import de.sciss.jump3r.lowlevel.LameEncoder
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.audio.AudioReceiveHandler
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.VoiceChannel
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import tech.gdragon.db.dao.Guild
import tech.gdragon.listener.CombinedAudioRecorderHandler
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.OffsetDateTime
import net.dv8tion.jda.core.entities.Guild as DiscordGuild


object BotUtils {
  private val logger = LoggerFactory.getLogger(BotUtils.javaClass)

  /**
   * Send a DM to anyone in the voiceChannel unless they are in the blacklist
   */
  @JvmStatic
  fun alert(voiceChannel: VoiceChannel?) {
    transaction {
      val guild = Guild.findById(voiceChannel?.guild?.idLong ?: 0)
      val blackList = guild?.settings?.alertBlacklist
      val message = EmbedBuilder()
        .setAuthor("pawabot", "https://github.com/guacamoledragon/throw-voice", voiceChannel?.jda?.selfUser?.avatarUrl)
        .setColor(Color.RED)
        .setTitle("Your audio is now being recorded in ${voiceChannel?.name} on ${voiceChannel?.guild?.name}.")
        .setDescription("Disable this alert with `${guild?.settings?.prefix}alerts off`")
        .setThumbnail("http://www.freeiconspng.com/uploads/alert-icon-png-red-alert-round-icon-clip-art-3.png")
        .setTimestamp(OffsetDateTime.now())
        .build()

      voiceChannel?.members
        ?.map { it.user }
        ?.filter { user -> !user.isBot && blackList?.find { it.id.value == user.idLong } == null }
        ?.forEach { user ->
          user.openPrivateChannel().queue { channel ->
            channel.sendMessage(message).queue()
          }
        }
    }
  }

  /**
   * Find biggest voice chanel that surpasses the Guild's autoJoin minimum
   */
  @JvmStatic
  fun biggestChannel(guild: DiscordGuild): VoiceChannel? {
    val voiceChannels = guild.voiceChannels

    return transaction {
      val settings = Guild.findById(guild.idLong)?.settings

      voiceChannels
        .filter { voiceChannel ->
          val channel = settings?.channels?.find { it.id.value == voiceChannel.idLong }
          val channelSize = voiceChannelSize(voiceChannel)
          channel?.autoJoin?.let { it <= channelSize } ?: false
        }
        .maxBy(BotUtils::voiceChannelSize)
    }
  }

  /**
   * General message sending utility with error logging
   */
  @JvmStatic
  fun sendMessage(textChannel: TextChannel?, msg: String) {
    textChannel
      ?.sendMessage("\u200B$msg")
      ?.queue(
        { m -> logger.debug("Successful Message: ${m.content}") },
        { t -> logger.error("Error Sending: $msg on ${textChannel.name}", t) }
      )
  }

  /**
   * Returns the effective size of the voice channel, excluding bots.
   */
  @JvmStatic
  fun voiceChannelSize(voiceChannel: VoiceChannel?): Int {
    return voiceChannel?.members?.count { !it.user.isBot } ?: 0
  }

  @JvmStatic
  fun joinVoiceChannel(voiceChannel: VoiceChannel?, warning: Boolean) {
    logger.info("Joining '{}' voice channel in {}.", voiceChannel?.name, voiceChannel?.guild?.name)

    if (voiceChannel == voiceChannel?.guild?.afkChannel) {
      if (warning) {
        transaction {
          val settings = Guild.findById(voiceChannel?.guild?.idLong ?: 0L)?.settings
          val channel = voiceChannel?.guild?.getTextChannelById(settings?.defaultTextChannel ?: 0L)
          sendMessage(channel, "I don't join afk channels!")
        }
      }
    }

    try {
      val audioManager = voiceChannel?.guild?.audioManager
      audioManager?.openAudioConnection(voiceChannel)
      alert(voiceChannel)
      transaction {
        val volume = Guild.findById(voiceChannel?.guild?.idLong ?: 0L)?.settings?.volume?.toDouble() ?: 0.8
        audioManager?.setReceivingHandler(CombinedAudioRecorderHandler(volume, voiceChannel))
      }
    } catch (e: InsufficientPermissionException) {
      logger.error("Not enough permissions to join ${voiceChannel?.name}", e)
      transaction {
        val settings = Guild.findById(voiceChannel?.guild?.idLong ?: 0L)?.settings
        val channel = voiceChannel?.guild?.getTextChannelById(settings?.defaultTextChannel ?: 0L)
        sendMessage(channel, "I don't have permission to join ${voiceChannel?.name}!")
      }
    }
  }

  @JvmStatic
  fun leaveVoiceChannel(voiceChannel: VoiceChannel?): AudioReceiveHandler {
    logger.info("Leaving '{}' voice channel in {}", voiceChannel?.name, voiceChannel?.guild?.name)

    voiceChannel
      ?.guild
      ?.audioManager
      ?.closeAudioConnection()

    return DiscordBot.killAudioHandlers(voiceChannel?.guild)
  }

  /**
   * Encodes PCM into Mp3, could probably make this a parallel function since we can write to different parts of the buffer
   */
  @JvmStatic
  fun encodePcmToMp3(pcm: ByteArray): ByteArray {
    LameEncoder.BITRATE_AUTO
    val encoder = LameEncoder(AudioReceiveHandler.OUTPUT_FORMAT, 128, LameEncoder.CHANNEL_MODE_AUTO, LameEncoder.QUALITY_HIGHEST, false)

    val mp3OutputStream = ByteArrayOutputStream()
    val buffer = ByteArray(encoder.pcmBufferSize)

    var bytesToTransfer = Math.min(buffer.size, pcm.size)
    var bytesWritten: Int
    var currentPcmPosition = 0

    bytesWritten = encoder.encodeBuffer(pcm, currentPcmPosition, bytesToTransfer, buffer)

    do {
      currentPcmPosition += bytesToTransfer
      bytesToTransfer = Math.min(buffer.size, pcm.size - currentPcmPosition)

      mp3OutputStream.write(buffer, 0, bytesWritten)

      bytesWritten = encoder.encodeBuffer(pcm, currentPcmPosition, bytesToTransfer, buffer)
    } while (0 < bytesWritten)

    val mp3 = mp3OutputStream.toByteArray()
    mp3OutputStream.reset()
    mp3OutputStream.close()

    return mp3
  }
}
