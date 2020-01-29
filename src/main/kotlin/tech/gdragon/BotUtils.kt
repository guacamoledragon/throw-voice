package tech.gdragon

import mu.KotlinLogging
import mu.withLoggingContext
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import tech.gdragon.db.asyncTransaction
import tech.gdragon.db.dao.Channel
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.table.Tables.Guilds
import tech.gdragon.listener.CombinedAudioRecorderHandler
import java.io.File
import java.io.FileInputStream
import net.dv8tion.jda.api.entities.Guild as DiscordGuild

object BotUtils {
  private val logger = KotlinLogging.logger {}

  /**
   * AutoRecord voice channel if it meets the auto record criterion
   */
  fun autoRecord(guild: DiscordGuild, channel: VoiceChannel) {
    val channelMemberCount = voiceChannelSize(channel)
    logger.debug { "Channel member count: $channelMemberCount" }

    transaction { Channel.findById(channel.idLong)?.autoRecord }
      ?.let {
        val autoRecord = it
        logger.debug { "AutoRecord value: $autoRecord" }

        if (channelMemberCount >= autoRecord) {
          val defaultChannel = defaultTextChannel(guild) ?: findPublicChannel(guild)

          withLoggingContext("guild" to guild.name, "voice-channel" to channel.name) {
            try {
              recordVoiceChannel(channel, defaultChannel) { ex ->
                val message = ":no_entry_sign: _Cannot record on **<#${channel.id}>**, need permission:_ ```${ex.permission}```"
                sendMessage(defaultChannel, message)
              }
            } catch (e: IllegalArgumentException) {
              logger.warn(e::message)
            }
          }
        }
      }
  }

  private fun autoSave(discordGuild: DiscordGuild): Boolean {
    return transaction {
      Guild
        .findById(discordGuild.idLong)
        ?.settings
        ?.autoSave
    } ?: false
  }

  fun autoStop(guild: DiscordGuild, channel: VoiceChannel) {
    val channelMemberCount = voiceChannelSize(channel)
    logger.debug { "${guild.name}#${channel.name} - Channel member count: $channelMemberCount" }

    transaction { Channel.findById(channel.idLong)?.autoStop }
      ?.let {
        val autoStop = it
        logger.debug { "${guild.name}#${channel.name} - autostop value: $autoStop" }

        if (channelMemberCount <= autoStop) {
          val defaultChannel = defaultTextChannel(guild) ?: findPublicChannel(guild)
          leaveVoiceChannel(channel, defaultChannel)
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

  // TODO: I don't think there's a need for the callback for exception handling, just throw
  fun recordVoiceChannel(channel: VoiceChannel, defaultChannel: TextChannel?, onError: (InsufficientPermissionException) -> Unit = {}) {

    /** Begin assertions **/
    require(defaultChannel != null && channel.guild.getTextChannelById(defaultChannel.id)?.canTalk() ?: false) {
      val msg = "Attempted to record, but bot cannot write to any channel."
      updateNickname(channel.guild.selfMember, "FIX ME")
      msg
    }

    // Bot won't connect to AFK channels
    require(channel != channel.guild.afkChannel) {
      val msg = ":no_entry_sign: _I'm not allowed to record AFK channels._"
      sendMessage(defaultChannel, msg)
      msg
    }

    /** End assertions **/

    val audioManager = channel.guild.audioManager

    if (audioManager.isConnected) {
      logger.debug { "vc:${channel.name} - Already connected to ${audioManager.connectedChannel?.name}" }
    } else {

      try {
        audioManager.openAudioConnection(channel)
        logger.info { "Connected to voice channel" }
      } catch (e: InsufficientPermissionException) {
        logger.warn { "Need permission: ${e.permission}" }
        onError(e)
        return
      }

      val volume = transaction {
        Guild.findById(channel.guild.idLong)
          ?.settings
          ?.volume
          ?.toDouble()
      } ?: 1.0

      val recorder = CombinedAudioRecorderHandler(volume, channel, defaultChannel)
      audioManager.receivingHandler = recorder

      recordingStatus(channel.guild.selfMember, true)
    }
  }

  @JvmStatic
  fun leaveVoiceChannel(voiceChannel: VoiceChannel, textChannel: TextChannel?) {
    val guild = voiceChannel.guild
    val audioManager = guild.audioManager
    val audioRecorderHandler = audioManager.receivingHandler as CombinedAudioRecorderHandler?

    withLoggingContext("guild" to voiceChannel.guild.name, "text-channel" to textChannel?.name.orEmpty()) {
      audioRecorderHandler?.let {
        if (autoSave(guild) && textChannel != null) {
          sendMessage(textChannel, ":floppy_disk: **Saving <#${voiceChannel.id}>'s recording...**")
          it.saveRecording(voiceChannel, textChannel)
        }

        it.disconnect()
      }

      logger.info("Leaving voice channel", guild.name, voiceChannel.name)
      audioManager.apply {
        receivingHandler = null
        sendingHandler = null
        closeAudioConnection()
        logger.info("Destroyed audio handlers", guild.name, voiceChannel.name)
      }

      recordingStatus(voiceChannel.guild.selfMember, false)
    }
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
          { t -> logger.error { "Error sending message - $msg: ${t.message}" } }
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
      updateNickname(bot, newNick)
    }
  }

  /**
   * Returns the effective size of the voice channel, excluding bots.
   */
  private fun voiceChannelSize(voiceChannel: VoiceChannel?): Int = voiceChannel?.members?.count() ?: 0

  /**
   * Leaves any Guild that hasn't been active in the past `afterDays` days.
   *
   * In the past, I've been deleting the Guild from the database, but that makes things annoying when you rejoin.
   * For now, we'll just be leaving a Guild, but keeping the settings.
   */
  fun leaveInactiveGuilds(jda: JDA, afterDays: Int, whitelist: List<Long>) {
    logger.info { "Leaving all Guilds that haven't been active in the past $afterDays days." }
    val op: SqlExpressionBuilder.() -> Op<Boolean> = {
      val now = DateTime.now()
      val from = now.minusDays(afterDays)
      val inactiveGuildOp = Guilds.active.eq(true).and(Guilds.lastActiveOn.less(from))

      if (whitelist.isNotEmpty()) {
        val whitelistEntityIDs = whitelist.map { EntityID(it, Guilds) }
        inactiveGuildOp.and(Guilds.id.notInList(whitelistEntityIDs))
      } else {
        inactiveGuildOp
      }
    }

    // Find all ancient guilds and ask the Bot to leave them, or mark them as inactive if already gone
    val guilds = transaction {
      Guild.find(op).map {
        object {
          val id = it.id.value
          val name = it.name
        }
      }
    }

    guilds
      .forEach {
        val guild = jda.shardManager?.getGuildById(it.id)
        guild
          ?.leave()
          ?.queue({
            logger.info { "Left server '$guild', reached inactivity period." }
          }, { e ->
            logger.error(e) { "Could not leave server '$guild'!" }
          })
          ?: logger.warn {
            asyncTransaction {
              Guild[it.id].active = false
            }
            "No longer in this guild ${it.name}, but marking as inactive"
          }
      }

    // Delete all ancient guilds using the same query as above
    /*transaction {
      Guilds.deleteWhere(op = op)
    }*/
  }

  /**
   * Finds an open channel where messages can be sent.
   */
  private fun findPublicChannel(guild: DiscordGuild): TextChannel? {
    return guild
      .textChannels
      .find(TextChannel::canTalk)
  }

  private fun updateNickname(bot: Member, nickname: String) {
    val prevNick = bot.effectiveName
    try {
      bot.guild
        .modifyNickname(bot, nickname)
        .queue(null, { t ->
          logger.error(t) {
            "Could not change nickname: $prevNick -> $nickname"
          }
        })
    } catch (e: InsufficientPermissionException) {
      logger.warn {
        "Missing ${e.permission} permission to change $prevNick -> $nickname"
      }
    }
  }

  /**
   * Update audio manager's receive volume if present
   */
  fun updateVolume(guild: DiscordGuild, volume: Double) {
    val handler =
      guild.audioManager.receivingHandler as CombinedAudioRecorderHandler?

    handler?.volume = volume
  }

  fun uploadFile(textChannel: TextChannel, file: File) {
    FileInputStream(file).use {
      try {
        textChannel
          .sendFile(it, file.name)
          .complete()
      } catch (e: InsufficientPermissionException) {
        withLoggingContext("guild" to textChannel.guild.name, "text-channel" to textChannel.name) {
          sendMessage(textChannel, ":no_entry_sign: _Couldn't upload recording directly to <#${textChannel.id}>, to enable this give `Attach Files` permissions._")
          logger.warn(e) {
            "Couldn't upload recording: ${file.name}"
          }
        }
      }
    }
  }
}
