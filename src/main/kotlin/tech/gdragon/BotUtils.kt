package tech.gdragon

import mu.KotlinLogging
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.entities.VoiceChannel
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import tech.gdragon.db.dao.Guild
import tech.gdragon.listener.CombinedAudioRecorderHandler
import java.awt.Color
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
        .setAuthor("pawa", "https://www.pawabot.site/", voiceChannel?.jda?.selfUser?.avatarUrl)
        .setColor(Color.RED)
        .setTitle("Your audio is now being recorded in ${voiceChannel?.name} on `${voiceChannel?.guild?.name}`.")
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
  @Deprecated("This contains a bug, any code using this should stop for now", level = DeprecationLevel.WARNING)
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


  fun isSelfBot(jda: JDA, user: User): Boolean {
    return user.isBot && jda.selfUser.idLong == user.idLong
  }

  /**
   * General message sending utility with error logging
   */
  @JvmStatic
  fun sendMessage(textChannel: TextChannel?, msg: String) {
    textChannel
      ?.sendMessage(msg)
      ?.queue(
        { m -> logger.debug("{}#{}: Send message - {}", m.guild.name, m.channel.name, m.contentDisplay) },
        { t -> logger.error("${textChannel.guild.name}#${textChannel.name}: Error sending message - $msg", t) }
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
    logger.info("{}#{}: Joining voice channel", voiceChannel?.guild?.name, voiceChannel?.name)

    if (voiceChannel == voiceChannel?.guild?.afkChannel) {
      if (warning) {
        transaction {
          val settings = Guild.findById(voiceChannel?.guild?.idLong ?: 0L)?.settings
          val channel = voiceChannel?.guild?.getTextChannelById(settings?.defaultTextChannel ?: 0L)
          sendMessage(channel, ":no_entry_sign: _I'm not allowed to join AFK channels._")
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
        sendMessage(channel, ":no_entry_sign: _I don't have permission to join **<#${voiceChannel?.id}>**._")
      }
    }
  }

  @JvmStatic
  fun leaveVoiceChannel(voiceChannel: VoiceChannel?) {
    val guild = voiceChannel?.guild
    val audioManager = guild?.audioManager
    val receiveHandler = audioManager?.receiveHandler as CombinedAudioRecorderHandler?

    receiveHandler?.apply {
      disconnect()
    }

    logger.info("{}#{}: Leaving voice channel", guild?.name, voiceChannel?.name)
    audioManager?.apply {
      setReceivingHandler(null)
      closeAudioConnection()
      logger.info("{}#{}: Destroyed audio handlers", guild.name, voiceChannel.name)
    }
  }
}
