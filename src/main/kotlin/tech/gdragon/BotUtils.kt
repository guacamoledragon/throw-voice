package tech.gdragon

import mu.KotlinLogging
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.entities.VoiceChannel
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.db.dao.Guild
import tech.gdragon.listener.CombinedAudioRecorderHandler
import tech.gdragon.listener.SilenceAudioSendHandler
import java.awt.Color
import java.time.OffsetDateTime
import java.util.*
import kotlin.concurrent.schedule
import net.dv8tion.jda.core.entities.Guild as DiscordGuild

object BotUtils {
  private val logger = KotlinLogging.logger {}

  /**
   * Send a DM to anyone in the voiceChannel unless they are in the blacklist
   */
  @Deprecated("Alerts no longer supported, will be removed on next version.", ReplaceWith("BotUtils.sendMessage(null, alertMessage)", "tech.gdragon"), DeprecationLevel.ERROR)
  fun alert(channel: VoiceChannel, alertMessage: String) {
    transaction {
      val guild = Guild.findById(channel.guild.idLong)
      val blackList = guild?.settings?.alertBlacklist
      val message = EmbedBuilder()
        .setAuthor("pawa", "https://www.pawabot.site/", channel.jda.selfUser.avatarUrl)
        .setColor(Color.RED)
        .setTitle("Alert!")
        .setDescription("""|$alertMessage
                           |
                           |Disable this alert with `${guild?.settings?.prefix}alerts off`""".trimMargin())
        .setThumbnail("http://www.freeiconspng.com/uploads/alert-icon-png-red-alert-round-icon-clip-art-3.png")
        .setTimestamp(OffsetDateTime.now())
        .build()

      channel.members
        ?.map { it.user }
        ?.filter { user -> !user.isBot && blackList?.find { it.name == user.id } == null }
        ?.forEach { user ->
          val errorHandler: ((t: Throwable) -> Unit) = {
            BotUtils.logger.warn {
              "${channel.guild.name}#${channel.name}: Could not alert ${user.name}"
            }
          }

          user.openPrivateChannel()
            .queue({ it.sendMessage(message).queue(null, errorHandler) }, errorHandler)
        }
    }
  }

  /**
   * AutoJoin voice channel if it meets the autojoining criterion
   */
  fun autoJoin(guild: DiscordGuild, channel: VoiceChannel, onError: (InsufficientPermissionException) -> String? = { _ -> null }): String? {
    val channelMemberCount = voiceChannelSize(channel)
    logger.debug { "${guild.name}#${channel.name} - Channel member count: $channelMemberCount" }

    return transaction {
      val settings = Guild.findById(guild.idLong)?.settings

      settings
        ?.channels
        ?.firstOrNull { it.id.value == channel.idLong }
        ?.let {
          val autoJoin = it.autoJoin
          BotUtils.logger.debug { "${guild.name}#${channel.name} - AutoJoin value: $autoJoin" }

          if (autoJoin != null && channelMemberCount >= autoJoin) {
            val defaultChannel = defaultTextChannel(guild)
            return@let joinVoiceChannel(channel, defaultChannel, onError = onError)
          }

          return@let null
        }
    }
  }

  fun autoSave(discordGuild: DiscordGuild): Boolean {
    return transaction {
      val guild = Guild.findById(discordGuild.idLong)
      guild?.settings?.autoSave ?: false
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

  /**
   * Obtain a reference to the default text channel one of these ways:
   * - Retrieve it based on the ID that the bot stores
   * - Retrieve the first channel that the bot can talk to
   */
  fun defaultTextChannel(discordGuild: DiscordGuild): MessageChannel? {
    return transaction {
        val guild = Guild.findById(discordGuild.idLong)
        val defaultChannelId = guild?.settings?.defaultTextChannel
        if (defaultChannelId == null) {
          (discordGuild.textChannels.find { it.canTalk() })?.also {
            val prefix = guild?.settings?.prefix
            val msg = """
              :warning: _The save location hasn't been set, please use `${prefix}saveLocation` to set.
              This channel will be used in the meantime. For more information use `${prefix}help`._
            """.trimIndent()
            sendMessage(it, msg)
          }
        } else {
          discordGuild.getTextChannelById(defaultChannelId)
        }
      }
  }

  fun isSelfBot(jda: JDA, user: User): Boolean {
    return user.isBot && jda.selfUser.idLong == user.idLong
  }

  fun joinVoiceChannel(channel: VoiceChannel, defaultChannel: MessageChannel?, warning: Boolean = false, onError: (InsufficientPermissionException) -> String? = { _ -> null }): String? {
    val saveLocation = defaultChannel ?: defaultTextChannel(channel.guild)

    if (saveLocation == null || !channel.guild.getTextChannelById(saveLocation.id).canTalk()) {
      logger.warn {
        val guild = channel.guild
        "${guild.name}:${channel.name}: Attempted to join, but bot cannot write to any channel."
      }

      return null
    }

    // TODO: Bot warns about AFK channel but connects anyway lulz
    if (channel == channel.guild.afkChannel) {
      if (warning) { // TODO: wtf does this do again?
        sendMessage(saveLocation, ":no_entry_sign: _I'm not allowed to join AFK channels._")
      }

      return null
    }

    val audioManager = channel.guild.audioManager

    if (audioManager?.isConnected == true) {
      logger.debug { "${channel.guild.name}#${channel.name} - Already connected to ${audioManager.connectedChannel.name}" }
    } else {

      try {
        audioManager?.openAudioConnection(channel)
        logger.info { "${channel.guild.name}#${channel.name} - Connected to voice channel" }
      } catch (e: InsufficientPermissionException) {
        logger.warn { "${channel.guild.name}#${channel.name} - Need permission: ${e.permission}" }
        return onError(e)
      }

      transaction {
        val volume = Guild.findById(channel.guild.idLong)
          ?.settings
          ?.volume
          ?.toDouble()
          ?: 1.0

        val audioSendHandler = SilenceAudioSendHandler()
        // Only send 5 seconds of audio at the beginning of the recording see: https://github.com/DV8FromTheWorld/JDA/issues/653
        Timer().schedule(5 * 1000) {
          audioSendHandler.canProvide = false
        }
        audioManager?.setReceivingHandler(CombinedAudioRecorderHandler(volume, channel, saveLocation))
        audioManager?.sendingHandler = audioSendHandler
        alert(channel, "Your audio is now being recorded in **<#${channel.id}>** on **${channel.guild.name}**.")
      }
    }

    return null
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
      sendingHandler = null
      closeAudioConnection()
      logger.info("{}#{}: Destroyed audio handlers", guild.name, voiceChannel.name)
    }
  }

  /**
   * General message sending utility with error logging
   */
  @JvmStatic
  fun sendMessage(textChannel: MessageChannel?, msg: String) {
    try {
      textChannel
        ?.sendMessage(msg)
        ?.queue(
          { m -> logger.debug("{}#{}: Send message - {}", m.guild.name, m.channel.name, m.contentDisplay) },
          { t -> logger.error("#${textChannel.name}: Error sending message - $msg", t) }
        )
    } catch (e: InsufficientPermissionException) {
      logger.warn(e) {
        "<insert guild name>#${textChannel?.name}: Missing permission ${e.permission}"
      }
    }
  }

  /**
   * Returns the effective size of the voice channel, excluding bots.
   */
  fun voiceChannelSize(voiceChannel: VoiceChannel?): Int = voiceChannel?.members?.count() ?: 0
}
