package tech.gdragon.listener

import mu.KotlinLogging
import mu.withLoggingContext
import net.dv8tion.jda.core.entities.Game
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.guild.GuildJoinEvent
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent
import net.dv8tion.jda.core.events.guild.member.GuildMemberNickChangeEvent
import net.dv8tion.jda.core.events.guild.update.GuildUpdateRegionEvent
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceJoinEvent
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceLeaveEvent
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceMoveEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.events.message.priv.PrivateMessageReceivedEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.KoinComponent
import tech.gdragon.BotUtils
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.commands.handleCommand
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.nowUTC
import net.dv8tion.jda.core.entities.Guild as DiscordGuild

class EventListener : ListenerAdapter(), KoinComponent {

  private val logger = KotlinLogging.logger {}
  val website: String = getKoin().getProperty("WEBSITE", "http://localhost:8080/")

  override fun onGuildUpdateRegion(event: GuildUpdateRegionEvent) {
    withLoggingContext("guild" to event.guild.name) {
      event.run {
        transaction {
          Guild.findOrCreate(guild.idLong, guild.name, event.oldRegion.name)
            .also {
              it.region = newRegion.name
            }
        }
        logger.info {
          "Changed region $oldRegion -> $newRegion"
        }
      }
    }
  }

  override fun onGuildJoin(event: GuildJoinEvent) {
    withLoggingContext("guild" to event.guild.name) {
      val guild = event.guild
      transaction {
        Guild
          .findOrCreate(guild.idLong, guild.name, guild.region.name)
          .also {
            it.active = true
            it.lastActiveOn = nowUTC()
          }
      }

      logger.info { "Joined new server '${guild.name}', connected to ${event.jda.guilds.size} guilds." }
    }
  }

  override fun onGuildLeave(event: GuildLeaveEvent) {
    withLoggingContext("guild" to event.guild.name) {
      transaction {
        Guild
          .findById(event.guild.idLong)
          ?.let {
            it.active = false
          }
      }

      logger.info { "Left server '${event.guild.name}', connected to ${event.jda.guilds.size} guilds." }
    }
  }

  override fun onGuildVoiceJoin(event: GuildVoiceJoinEvent) {
    withLoggingContext("guild" to event.guild.name) {
      val user = event.member.user
      logger.debug { "${event.guild.name}#${event.channelJoined.name} - ${user.name} joined voice channel" }

      if (BotUtils.isSelfBot(user)) {
        logger.debug { "${event.guild.name}#${event.channelJoined.name} - ${user.name} is self-bot" }
        return
      }

      // Update activity
      transaction {
        event.guild.run {
          Guild
            .findOrCreate(idLong, name, region.name)
            .also {
              it.lastActiveOn = nowUTC()
            }
        }
      }

      BotUtils.autoRecord(event.guild, event.channelJoined)
    }
  }

  override fun onGuildVoiceLeave(event: GuildVoiceLeaveEvent) {
    withLoggingContext("guild" to event.guild.name) {
      logger.debug { "${event.guild.name}#${event.channelLeft.name} - ${event.member.effectiveName} left voice channel" }
      if (BotUtils.isSelfBot(event.member.user).not()) {
        BotUtils.autoStop(event.guild, event.channelLeft)
      }
    }
  }

  override fun onGuildVoiceMove(event: GuildVoiceMoveEvent) {
    withLoggingContext("guild" to event.guild.name) {
      val user = event.member.user
      logger.debug { "${event.guild.name}#${event.channelLeft.name} - ${user.name} left voice channel" }
      logger.debug { "${event.guild.name}#${event.channelJoined.name} - ${user.name} joined voice channel" }

      // Update activity
      transaction {
        event.guild.run {
          Guild
            .findOrCreate(idLong, name, region.name)
            .also {
              it.lastActiveOn = nowUTC()
            }
        }
      }

      if (BotUtils.isSelfBot(user).not()) {
        BotUtils.autoStop(event.guild, event.channelLeft)
        BotUtils.autoRecord(event.guild, event.channelJoined)
      }
    }
  }

  override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
    if (event.member == null || event.member.user == null || BotUtils.isSelfBot(event.member.user))
      return

    val guildId = event.guild.idLong

    val prefix = transaction {
      // HACK: Create settings for a guild that needs to be accessed. This is a problem when restarting bot.
      // TODO: On bot initialization, I should be able to check which Guilds the bot is connected to and purge/add respectively
      val guild = Guild.findById(guildId) ?: Guild.findOrCreate(guildId, event.guild.name, event.guild.region.name)

      // Update activity
      guild.lastActiveOn = nowUTC()

      guild.settings.prefix
    }

    val rawContent = event.message.contentDisplay.toLowerCase()
    val hasPrefix = rawContent.startsWith(prefix)
    val inMaintenance = getKoin().getProperty("MAINTENANCE", "false").toBoolean()
    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel

    withLoggingContext("guild" to event.guild.name, "text-channel" to event.channel.name) {
      when {
        hasPrefix && inMaintenance -> {
          BotUtils.sendMessage(defaultChannel, ":poop: _Currently running an update...\n\nIf you have any questions please visit the support server: ${website}_")
          logger.warn("Trying to use while running an update")
        }
        hasPrefix ->
          try {
            handleCommand(event, prefix, rawContent)
          } catch (e: InvalidCommand) {
            BotUtils.sendMessage(defaultChannel, ":no_entry_sign: _Usage: `${e.usage(prefix)}`_")
            logger.warn { "[$rawContent] ${e.reason}" }
          }
      }
    }
  }

  override fun onPrivateMessageReceived(event: PrivateMessageReceivedEvent) {
    if (event.author.isBot.not()) {
      val message = """
        For more information on ${event.jda.selfUser.asMention}, please visit https://www.pawa.im.
      """.trimIndent()

      event
        .channel
        .sendMessage(message)
        .queue()
    }
  }

  /**
   * Always add recording prefix when recording and if possible.
   */
  override fun onGuildMemberNickChange(event: GuildMemberNickChangeEvent) {
    if (BotUtils.isSelfBot(event.user)) {
      if (event.guild.audioManager.isConnected) {
        logger.debug {
          "${event.guild}#: Attempting to change nickname from ${event.prevNick} -> ${event.newNick}"
        }

        BotUtils.recordingStatus(event.member, true)
      }
    }
  }

  override fun onReady(event: ReadyEvent) {
    val version: String = getKoin().getProperty("VERSION", "dev")
    event
      .jda
      .presence.game = object : Game("$version | $website", website, Game.GameType.DEFAULT) {}

    logger.info { "ONLINE: Connected to ${event.jda.guilds.size} guilds!" }

    // Add guild if not present

    logger.info { "Add any missing Guilds to the Database..." }
    event.jda.guilds.forEach {
      transaction {
        tech.gdragon.db.dao.Guild.findOrCreate(it.idLong, it.name, it.region.name)
      }
    }
  }
}
