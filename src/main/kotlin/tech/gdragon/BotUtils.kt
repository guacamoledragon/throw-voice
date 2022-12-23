package tech.gdragon

import com.github.benmanes.caffeine.cache.Caffeine
import io.opentelemetry.extension.annotations.WithSpan
import mu.KotlinLogging
import mu.withLoggingContext
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import net.dv8tion.jda.internal.managers.AudioManagerImpl
import org.jetbrains.exposed.dao.exceptions.EntityNotFoundException
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.db.asyncTransaction
import tech.gdragon.db.dao.Channel
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.now
import tech.gdragon.db.table.Tables.Guilds
import tech.gdragon.listener.CombinedAudioRecorderHandler
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import net.dv8tion.jda.api.entities.Guild as DiscordGuild
import tech.gdragon.i18n.Record as RecordTranslator

object BotUtils {
  private val logger = KotlinLogging.logger {}

  const val trigoman = 96802905322962944L

  private val guildActivityCache = Caffeine.newBuilder()
    .expireAfterWrite(1L, TimeUnit.HOURS)
    .softValues()
    .build<Long, Instant>()

  val guildCache = Caffeine.newBuilder()
    .build<Long, String>()

  /**
   * AutoRecord voice channel if it meets the auto record criterion
   */
  fun autoRecord(pawa: Pawa, guild: DiscordGuild, channel: VoiceChannel) {
    val channelMemberCount = voiceChannelSize(channel)
    logger.debug { "Channel member count: $channelMemberCount" }

    transaction { Channel.findById(channel.idLong)?.autoRecord }
      ?.let {
        val autoRecord = it
        logger.debug { "AutoRecord value: $autoRecord" }

        if (channelMemberCount >= autoRecord) {
          val defaultChannel = defaultTextChannel(guild) ?: findPublicChannel(guild)

          withLoggingContext("guild" to guild.name, "voice-channel" to channel.name) {
            val audioManager = guild.audioManager
            val translator: RecordTranslator = pawa.translator(guild.idLong)
            if (audioManager.isConnected) {
              sendMessage(
                defaultChannel,
                ":no_entry_sign: _${translator.alreadyInChannel(audioManager.connectedChannel!!.id)}_"
              )
            } else {
              val message =
                try {
                  val recorder = recordVoiceChannel(channel)
                  pawa.startRecording(recorder.session, guild.idLong)
                  translator.recording(channel.id, recorder.session)
                } catch (e: IllegalArgumentException) {
                  when (e.message) {
                    "no-write-permission" ->
                      "Attempted to record, but bot cannot write to any channel."

                    "no-speak-permission" ->
                      ":no_entry_sign: _${translator.cannotRecord(channel.id, Permission.VOICE_CONNECT.name)}_"

                    "no-attach-files-permission" ->
                      translator.cannotUpload(defaultChannel!!.id, Permission.MESSAGE_ATTACH_FILES.name)

                    "afk-channel" ->
                      ":no_entry_sign: _${translator.afkChannel(channel.id)}_"

                    else ->
                      ":no_entry_sign: _Unknown bad argument: ${e.message}_"
                  }
                }
              sendMessage(defaultChannel, message)
            }
          }
        }
      }
  }

  @WithSpan("Auto Save")
  fun autoSave(discordGuild: DiscordGuild): Boolean {
    return transaction {
      Guild
        .findById(discordGuild.idLong)
        ?.settings
        ?.autoSave
    } ?: false
  }

  fun autoStop(guild: DiscordGuild, channel: VoiceChannel) {
    if (guild.audioManager.connectedChannel == channel) {
      val channelMemberCount = voiceChannelSize(channel)
      logger.debug { "${guild.name}#${channel.name} - Channel member count: $channelMemberCount" }

      transaction { Channel.findById(channel.idLong)?.autoStop }
        ?.let {
          val autoStop = it
          logger.debug { "${guild.name}#${channel.name} - autostop value: $autoStop" }

          if (channelMemberCount <= autoStop) {
            val defaultChannel = defaultTextChannel(guild) ?: findPublicChannel(guild)
            val save = autoSave(guild)
            leaveVoiceChannel(channel, defaultChannel, save)
          }
        }
    } else {
      logger.debug {
        "Not connected to $channel, can't leave it ;)"
      }
    }
  }

  /**
   * Obtain a reference to the default text channel one of these ways:
   * - Retrieve it based on the ID that the bot stores
   * - Retrieve the first channel that the bot can talk to
   */
  @WithSpan("Guild Default Channel")
  fun defaultTextChannel(guild: DiscordGuild): TextChannel? {
    return transaction {
      Guild
        .findById(guild.idLong)
        ?.settings
        ?.defaultTextChannel
        ?.let(guild::getTextChannelById)
    }
  }

  /**
   * Finds an open channel where messages can be sent.
   */
  fun findPublicChannel(guild: DiscordGuild): TextChannel? {
    return guild
      .textChannels
      .find(TextChannel::canTalk)
  }

  @WithSpan("Get Guild Prefix")
  fun getPrefix(guild: DiscordGuild): String {
    return guild.run {
      guildCache.getIfPresent(idLong) ?: transaction {
        logger.debug { "Cache Miss! Obtaining prefix for $idLong" }
        // HACK: Create settings for a guild that needs to be accessed. This is a problem when restarting bot.
        // TODO: On bot initialization, I should be able to check which Guilds the bot is connected to and purge/add respectively
        val prefix = Guild.findOrCreate(idLong, name, region.name).settings.prefix
        guildCache.put(idLong, prefix)
        prefix
      }
    }
  }

  fun setPrefix(guild: DiscordGuild, newPrefix: String): String {
    return guild.run {
      transaction {
        val prefix = Guild.findById(idLong)!!.settings.apply { prefix = newPrefix }.prefix
        guildCache.put(idLong, prefix)
        prefix
      }
    }
  }

  @WithSpan("Is Self Bot")
  fun isSelfBot(user: User): Boolean {
    return user.isBot && user.jda.selfUser.idLong == user.idLong
  }

  @WithSpan("Leave Voice Channel")
  @JvmStatic
  fun leaveVoiceChannel(
    voiceChannel: VoiceChannel,
    textChannel: TextChannel?,
    save: Boolean
  ): CombinedAudioRecorderHandler {
    // TODO: This method should be broken up into two, one that stops and saves and another one that leaves voice channel
    val guild = voiceChannel.guild
    val audioManager = guild.audioManager as AudioManagerImpl
    val recorder = audioManager.receivingHandler as CombinedAudioRecorderHandler

    withLoggingContext("guild" to voiceChannel.guild.name, "text-channel" to textChannel?.name.orEmpty()) {
      val (recording, recordingLock) =
        if (save && textChannel != null) {
          sendMessage(textChannel, ":floppy_disk: **Saving <#${voiceChannel.id}>'s recording...**")
          recorder.saveRecording(voiceChannel, textChannel, false)
        } else Pair(null, null)

      recorder.disconnect(!save, recording, recordingLock)

      logger.debug { "Leaving voice channel" }
      audioManager.apply {
        audioConnection.close(ConnectionStatus.NOT_CONNECTED)
        closeAudioConnection()
        logger.debug { "Destroyed audio handlers" }
      }

      recordingStatus(voiceChannel.guild.selfMember, false)
    }

    return recorder
  }

  /**
   * Leaves any Guild that hasn't been active in the past `afterDays` days.
   *
   * In the past, I've been deleting the Guild from the database, but that makes things annoying when you rejoin.
   * For now, we'll just be leaving a Guild, but keeping the settings.
   */
  fun leaveInactiveGuilds(jda: JDA, afterDays: Long, whitelist: List<Long>): Int {
    logger.info { "Leaving all Guilds that haven't been active in the past $afterDays days." }
    val op: SqlExpressionBuilder.() -> Op<Boolean> = {
      val now = LocalDate.now()
      val from = now
        .minusDays(afterDays)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
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

    return guilds.size
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
   * Starts recording on [channel] and sends any communication to [defaultChannel]
   *
   * @throws IllegalStateException when bot cannot write in provided [defaultChannel]
   */
  @WithSpan("Record Voice Channel")
  fun recordVoiceChannel(
    channel: VoiceChannel,
    defaultChannel: TextChannel? = defaultTextChannel(channel.guild) ?: findPublicChannel(channel.guild)
  ): CombinedAudioRecorderHandler {
    require(defaultChannel != null && defaultChannel.canTalk()) {
      updateNickname(channel.guild.selfMember, "CANNOT WRITE")
      "no-write-permission"
    }

    require(channel.guild.selfMember.hasPermission(channel, Permission.VOICE_CONNECT)) {
      logger.info {
        "User ${channel.guild.selfMember.effectiveName} does not have permission to connect to ${channel.name}"
      }
      "no-speak-permission"
    }

    require(defaultChannel.guild.selfMember.hasPermission(defaultChannel, Permission.MESSAGE_ATTACH_FILES)) {
      logger.info {
        "User ${defaultChannel.guild.selfMember.effectiveName} does not have permission to attach files to ${defaultChannel.name}"
      }
      "no-attach-files-permission"
    }

    require(channel != channel.guild.afkChannel) {
      "afk-channel"
    }

    val audioManager = channel.guild.audioManager
    audioManager.openAudioConnection(channel)
    logger.info { "Connected to voice channel" }

    val volume = transaction {
      Guild.findById(channel.guild.idLong)
        ?.settings
        ?.volume
        ?.toDouble()
    } ?: 1.0

    val recorder = CombinedAudioRecorderHandler(volume, channel, defaultChannel)
    audioManager.receivingHandler = recorder
    recordingStatus(channel.guild.selfMember, true)

    return recorder
  }

  /**
   * General message sending utility with error logging
   */
  @WithSpan("Send Text Message")
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

  fun sendEmbedMessage(textChannel: MessageChannel, embedMessage: MessageEmbed) {
    textChannel
      .sendMessage(embedMessage)
      .queue(
        { m -> logger.debug { "Send message - ${m.contentDisplay}" } },
        { t -> logger.error { "Error sending message - $embedMessage.: ${t.message}" } }
      )
  }

  @WithSpan("Update Guild Activity")
    /**
     * Using an LRU cache, update activity if not in cache, this is not thread safe but also non-critical so wutevs
     */
  fun updateActivity(guild: DiscordGuild): Unit {
    if (guildActivityCache.getIfPresent(guild.idLong) == null) {
      // Update Guild name if necessary
      updateGuildName(guild)

      // Update LRU
      guildActivityCache.put(guild.idLong, now())

      // Update active on timestamp
      asyncTransaction {
        Guild[guild.idLong].lastActiveOn = guildActivityCache.getIfPresent(guild.idLong)!!
      }
    }
  }

  fun uploadFile(textChannel: TextChannel, file: File, filename: String): Message? {
    var msgResult: Message? = null

    FileInputStream(file).use {
      msgResult = textChannel
        .sendFile(it, filename)
        .complete()
    }

    return msgResult
  }

  /**
   * Update Guild name if different than what's on the database
   */
  private fun updateGuildName(guild: DiscordGuild) {
    val newName = guild.name
    val oldName = transaction {
      try {
        Guild[guild.idLong].name
      } catch (e: EntityNotFoundException) {
        logger.warn(e) {
          "Couldn't find Guild with ID: ${guild.idLong} \tNew Name: $newName"
        }
        newName
      }
    }

    if (oldName != newName) {
      asyncTransaction {
        withLoggingContext("guild" to newName) {
          Guild[guild.idLong].name = newName

          logger.info {
            "Changed name $oldName -> $newName"
          }
        }
      }
    }
  }

  private fun updateNickname(bot: Member, nickname: String) {
    val prevNick = bot.effectiveName
    try {
      bot.guild
        .modifyNickname(bot, nickname)
        .reason("Represent bot's recording state.")
        .queue(null) { t ->
          logger.error(t) {
            "Could not change nickname: $prevNick -> $nickname"
          }
        }
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

  /**
   * Returns the effective size of the voice channel, excluding bots.
   */
  private fun voiceChannelSize(voiceChannel: VoiceChannel?): Int = voiceChannel?.members?.count() ?: 0
}
