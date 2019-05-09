package tech.gdragon

import mu.KotlinLogging
import mu.withLoggingContext
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.entities.*
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException
import org.jetbrains.exposed.sql.Between
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.not
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.dateTimeLiteral
import tech.gdragon.db.table.Tables
import tech.gdragon.listener.CombinedAudioRecorderHandler
import tech.gdragon.listener.SilenceAudioSendHandler
import java.io.File
import java.util.*
import kotlin.concurrent.schedule
import net.dv8tion.jda.core.entities.Guild as DiscordGuild

object BotUtils {
  private val logger = KotlinLogging.logger {}

  /**
   * AutoRecord voice channel if it meets the auto record criterion
   */
  fun autoRecord(guild: DiscordGuild, channel: VoiceChannel) {
    val channelMemberCount = voiceChannelSize(channel)
    logger.debug { "Channel member count: $channelMemberCount" }

    return transaction {
      val settings = Guild.findById(guild.idLong)?.settings

      settings
        ?.channels
        ?.firstOrNull { it.id.value == channel.idLong }
        ?.let {
          val autoRecord = it.autoRecord
          BotUtils.logger.debug { "AutoRecord value: $autoRecord" }

          if (autoRecord != null && channelMemberCount >= autoRecord) {
            val defaultChannel = defaultTextChannel(guild) ?: findPublicChannel(guild)

            withLoggingContext("guild" to guild.name, "voice-channel" to channel.name) {
              try {
                recordVoiceChannel(channel, defaultChannel) { ex ->
                  val message = ":no_entry_sign: _Cannot record on **<#${channel.id}>**, need permission:_ ```${ex.permission}```"
                  sendMessage(defaultChannel, message)
                }
              } catch (e: IllegalArgumentException) {
                BotUtils.logger.error(e) {
                  e.message
                }
              }
            }
          }
        }
    }
  }

  fun autoSave(discordGuild: DiscordGuild): Boolean {
    return transaction {
      val guild = Guild.findById(discordGuild.idLong)
      guild?.settings?.autoSave ?: false
    }
  }

  fun autoStop(guild: DiscordGuild, channel: VoiceChannel) {
    val channelMemberCount = voiceChannelSize(channel)
    logger.debug { "${guild.name}#${channel.name} - Channel member count: $channelMemberCount" }

    transaction {
      val settings = Guild.findById(guild.idLong)?.settings

      settings
        ?.channels
        ?.firstOrNull { it.id.value == channel.idLong }
        ?.let {
          val autoStop = it.autoStop
          BotUtils.logger.debug { "${guild.name}#${channel.name} - autostop value: $autoStop" }

          if (autoStop != null && channelMemberCount <= autoStop) {
            val defaultChannel = defaultTextChannel(guild) ?: findPublicChannel(guild)
            leaveVoiceChannel(channel, defaultChannel)
          }
        }
    }
  }

  /**
   * Obtain a reference to the default text channel one of these ways:
   * - Retrieve it based on the ID that the bot stores
   * - Retrieve the first channel that the bot can talk to
   */
  fun defaultTextChannel(guild: DiscordGuild): TextChannel? {
    return transaction {
      Guild
        .findById(guild.idLong)
        ?.settings
        ?.defaultTextChannel
        ?.let(guild::getTextChannelById)
    }
  }

  fun isSelfBot(user: User): Boolean {
    return user.isBot && user.jda.selfUser.idLong == user.idLong
  }

  fun recordVoiceChannel(channel: VoiceChannel, defaultChannel: TextChannel?, onError: (InsufficientPermissionException) -> Unit = {}) {

    /** Begin assertions **/
    require(defaultChannel != null && channel.guild.getTextChannelById(defaultChannel.id).canTalk()) {
      val msg = "Attempted to record, but bot cannot write to any channel."
      logger.warn(msg)
      msg
    }

    // Bot won't connect to AFK channels
    require(channel != channel.guild.afkChannel) {
      val msg = ":no_entry_sign: _I'm not allowed to record AFK channels._"
      sendMessage(defaultChannel, msg)
      logger.warn(msg)
      msg
    }

    /** End assertions **/

    val audioManager = channel.guild.audioManager

    if (audioManager?.isConnected == true) {
      logger.debug { "vc:${channel.name} - Already connected to ${audioManager.connectedChannel.name}" }
    } else {

      try {
        audioManager?.openAudioConnection(channel)
        logger.info { "vc:${channel.name} - Connected to voice channel" }
      } catch (e: InsufficientPermissionException) {
        logger.warn { "vc:${channel.name} - Need permission: ${e.permission}" }
        onError(e)
        return
      }

      val volume = transaction {
        Guild.findById(channel.guild.idLong)
          ?.settings
          ?.volume
          ?.toDouble()
          ?: 1.0
      }

      val recorder = CombinedAudioRecorderHandler(volume, channel, defaultChannel)
      val audioSendHandler = SilenceAudioSendHandler()

      // Only send 5 seconds of audio at the beginning of the recording see: https://github.com/DV8FromTheWorld/JDA/issues/653
      Timer().schedule(5 * 1000) {
        audioSendHandler.canProvide = false
      }

      audioManager?.setReceivingHandler(recorder)
      audioManager?.sendingHandler = audioSendHandler

      recordingStatus(channel.guild.selfMember, true)
      sendMessage(defaultChannel, ":red_circle: **Audio is being recorded on <#${channel.id}>**")
    }
  }

  @JvmStatic
  fun leaveVoiceChannel(voiceChannel: VoiceChannel, textChannel: TextChannel?) {
    val guild = voiceChannel.guild
    val audioManager = guild?.audioManager
    val receiveHandler = audioManager?.receiveHandler as CombinedAudioRecorderHandler?

    receiveHandler?.let {
      if (autoSave(guild) && textChannel != null) {
        sendMessage(textChannel, ":floppy_disk: **Saving <#${voiceChannel.id}>'s recording...**")
        it.saveRecording(voiceChannel, textChannel)
      }

      it.disconnect()
    }

    logger.info("{}#{}: Leaving voice channel", guild?.name, voiceChannel.name)
    audioManager?.apply {
      setReceivingHandler(null)
      sendingHandler = null
      closeAudioConnection()
      logger.info("{}#{}: Destroyed audio handlers", guild.name, voiceChannel.name)
    }

    recordingStatus(voiceChannel.guild.selfMember, false)
  }

  /**
   * General message sending utility with error logging
   */
  fun sendMessage(textChannel: MessageChannel?, msg: String) {
    try {
      textChannel
        ?.sendMessage(msg)
        ?.queue(
          { m -> logger.debug { "Send message - ${m.contentDisplay}" } },
          { logger.error { "Error sending message - $msg" } }
        )
    } catch (e: InsufficientPermissionException) {
      logger.warn(e) {
        "Missing permission ${e.permission}"
      }
    }
  }

  /**
   * Change the bot's nickname depending on it's recording status.
   *
   * There are a few edge cases that I don't feel like fixing, for instance
   * if the nickname exceeds 32 characters, then it's just not renamed. Additionally,
   * if the blocking call to change the nick fails, it'll just leave it as it was as
   * set by the user.
   */
  fun recordingStatus(bot: Member, isRecording: Boolean) {
    val prefix = "[REC]"
    val prevNick = bot.effectiveName

    val newNick = if (isRecording) {
      if (prevNick.startsWith(prefix).not()) {
        prefix + prevNick
      } else {
        prevNick
      }
    } else {
      if (prevNick.startsWith(prefix)) {
        prevNick.removePrefix(prefix)
      } else {
        prevNick
      }
    }

    if (newNick != prevNick && (newNick.length <= 32)) {
      try {
        bot.guild.controller
          .setNickname(bot, newNick)
          .queue(null, { t ->
            logger.error(t) {
              "Could not change nickname: $prevNick -> $newNick"
            }
          })
      } catch (e: InsufficientPermissionException) {
        logger.warn(e) {
          "Missing ${e.permission} permission to change $prevNick -> $newNick"
        }
      }
    }
  }

  /**
   * Returns the effective size of the voice channel, excluding bots.
   */
  fun voiceChannelSize(voiceChannel: VoiceChannel?): Int = voiceChannel?.members?.count() ?: 0

  /**
   * Leaves any Guild that hasn't been active in the past 30 days.
   *
   * In the past, I've been deleting the Guild from the database, but that makes things annoying when you rejoin.
   * For now, we'll just be leaving a Guild, but keeping the settings.
   */
  fun leaveAncientGuilds(jda: JDA) {
    val op: SqlExpressionBuilder.() -> Op<Boolean> = {
      val now = DateTime.now()
      not(Between(Tables.Guilds.lastActiveOn, dateTimeLiteral(now.minusDays(30)), dateTimeLiteral(now)))
    }

    // Find all ancient guilds and ask the Bot to leave them
    transaction {
      Guild.find(op).toList()
    }.forEach {
      val guild = jda.getGuildById(it.id.value)
      guild
        ?.leave()
        ?.queue({
          logger.info { "Left server '$guild', reached inactivity period." }
        }, { e ->
          logger.error(e) { "Could not leave server '$guild'!" }
        })
    }

    // Delete all ancient guilds using the same query as above
    /*transaction {
      Guilds.deleteWhere(op = op)
    }*/
  }

  /**
   * Finds an open channel where messages can be sent.
   */
  fun findPublicChannel(guild: DiscordGuild): TextChannel? {
    return guild
      .textChannels
      .find(TextChannel::canTalk)
  }

  /**
   * Update audio manager's receive volume if present
   */
  fun updateVolume(guild: DiscordGuild, volume: Double) {
    val handler =
      guild.audioManager
        ?.receiveHandler as CombinedAudioRecorderHandler?

    handler?.volume = volume
  }

  fun uploadFile(textChannel: TextChannel, file: File) {
    val message = MessageBuilder()
      .append(":arrow_up: **Upload complete!**")
      .build()

    textChannel
      .sendFile(file, message)
      .queue()
  }
}
