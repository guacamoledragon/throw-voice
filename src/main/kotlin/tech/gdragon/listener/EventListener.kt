package tech.gdragon.listener

import mu.KotlinLogging
import mu.withLoggingContext
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent
import net.dv8tion.jda.api.events.guild.update.GuildUpdateRegionEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import tech.gdragon.BotUtils
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.commands.handleCommand
import tech.gdragon.db.asyncTransaction
import tech.gdragon.db.dao.Guild

class EventListener : ListenerAdapter(), KoinComponent {

  private val logger = KotlinLogging.logger {}
  private val website: String = getKoin().getProperty("BOT_WEBSITE", "http://localhost:8080/")
  private val standalone = getKoin().getProperty<String>("BOT_STANDALONE").toBoolean()

  override fun onGuildJoin(event: GuildJoinEvent) {
    withLoggingContext("guild" to event.guild.name) {
      val guild = event.guild
      asyncTransaction {
        Guild
          .findOrCreate(guild.idLong, guild.name, guild.region.name)
          .also {
            it.active = true
          }
        BotUtils.updateActivity(event.guild)
      }

      logger.info { "Joined new server '${guild.name}', connected to ${event.jda.guilds.size} guilds." }
    }
  }

  override fun onGuildLeave(event: GuildLeaveEvent) {
    withLoggingContext("guild" to event.guild.name) {
      asyncTransaction {
        Guild
          .findById(event.guild.idLong)
          ?.let {
            it.active = false
          }
      }

      logger.info { "Left server '${event.guild.name}', connected to ${event.jda.guilds.size} guilds." }
    }
  }

  override fun onGuildUpdateRegion(event: GuildUpdateRegionEvent) {
    withLoggingContext("guild" to event.guild.name) {
      event.run {
        asyncTransaction {
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

  override fun onGuildVoiceJoin(event: GuildVoiceJoinEvent) {
    withLoggingContext("guild" to event.guild.name) {
      val user = event.member.user
      logger.debug { "${event.guild.name}#${event.channelJoined.name} - ${user.name} joined voice channel" }

      if (BotUtils.isSelfBot(user)) {
        logger.debug { "${event.guild.name}#${event.channelJoined.name} - ${user.name} is self-bot" }
        return
      }

      // Update activity
      BotUtils.updateActivity(event.guild)

      if (standalone) {
        BotUtils.autoRecord(event.guild, event.channelJoined)
      }
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
      BotUtils.updateActivity(event.guild)

      if (BotUtils.isSelfBot(user).not()) {
        BotUtils.autoStop(event.guild, event.channelLeft)
        if (standalone) {
          BotUtils.autoRecord(event.guild, event.channelJoined)
        }
      }
    }
  }

  override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
    event.member?.let {
      if (BotUtils.isSelfBot(it.user)) return
    } ?: return

    val prefix = BotUtils.getPrefix(event.guild)

    withLoggingContext("guild" to event.guild.name, "text-channel" to event.channel.name) {
      val rawContent = event.message.contentDisplay.toLowerCase()
      val inMaintenance = getKoin().getProperty("BOT_MAINTENANCE", "false").toBoolean()
      val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
      val hasPrefix = rawContent.startsWith(prefix)

      if (hasPrefix && inMaintenance) {
        BotUtils.sendMessage(
          defaultChannel,
          ":poop: _Currently running an update...\n\nIf you have any questions please visit the support server: ${website}_"
        )
        logger.warn("Trying to use while running an update")
      } else if (hasPrefix)
        try {
          handleCommand(event, prefix, rawContent, event.message)
          // Update activity
          BotUtils.updateActivity(event.guild)
        } catch (e: InvalidCommand) {
          BotUtils.sendMessage(defaultChannel, ":no_entry_sign: _Usage: `${e.usage(prefix)}`_")
          logger.warn { "[$rawContent] ${e.reason}" }
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
  override fun onGuildMemberUpdateNickname(event: GuildMemberUpdateNicknameEvent) {
    if (BotUtils.isSelfBot(event.user)) {
      if (event.guild.audioManager.isConnected) {
        logger.debug {
          "${event.guild}#: Attempting to change nickname from ${event.oldNickname} -> ${event.newNickname}"
        }

        BotUtils.recordingStatus(event.member, true)
      }
    }
  }

  override fun onReady(event: ReadyEvent) {
    val version: String = getKoin().getProperty("BOT_ACTIVITY", "dev")
    event
      .jda
      .presence
      .activity = Activity.of(Activity.ActivityType.DEFAULT, "$version | $website", website)

    // Add guild if not present
    logger.info { "Add any missing Guilds to the Database..." }
    event.jda.shardManager?.guilds?.forEach {
      transaction {
        Guild.findOrCreate(it.idLong, it.name, it.region.name)
      }
    }

    logger.info { "ONLINE: Connected to ${event.jda.shardManager?.guilds?.size} guilds!" }
  }
}
