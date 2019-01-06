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
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import net.dv8tion.jda.core.entities.Guild as DiscordGuild

class EventListener : ListenerAdapter() {

  private val logger = KotlinLogging.logger {}

  override fun onGuildUpdateRegion(event: GuildUpdateRegionEvent) {
    withLoggingContext("guild" to event.guild.name) {
      transaction {
        this@EventListener.logger.info {
          "Changed region ${event.oldRegion} -> ${event.newRegion}"
        }
        event.guild.run {
          val guild = Guild.findOrCreate(idLong, name, event.oldRegion.name)
          guild.region = event.newRegion.name
        }
      }
    }
  }

  override fun onGuildJoin(event: GuildJoinEvent) {
    transaction {
      val guild = event.guild
      Guild.findOrCreate(guild.idLong, guild.name, guild.region.name)
    }

    logger.info { "Joined new server '${event.guild.name}', connected to ${event.jda.guilds.size} guilds." }
  }

  override fun onGuildLeave(event: GuildLeaveEvent) {
    /*transaction {
      val guild = Guild.findById(event.guild.idLong)
      guild?.delete()
    }*/

    logger.info { "Left server '${event.guild.name}', connected to ${event.jda.guilds.size} guilds." }
  }

  override fun onGuildVoiceJoin(event: GuildVoiceJoinEvent) {
    val user = event.member.user
    logger.debug { "${event.guild.name}#${event.channelJoined.name} - ${user.name} joined voice channel" }

    if (BotUtils.isSelfBot(event.jda, user)) {
      logger.debug { "${event.guild.name}#${event.channelJoined.name} - ${user.name} is self-bot" }
      return
    }

    BotUtils.autoJoin(event.guild, event.channelJoined)
  }

  override fun onGuildVoiceLeave(event: GuildVoiceLeaveEvent) {
    logger.debug { "${event.guild.name}#${event.channelLeft.name} - ${event.member.effectiveName} left voice channel" }
  }

  override fun onGuildVoiceMove(event: GuildVoiceMoveEvent) {
    val user = event.member.user
    logger.debug { "${event.guild.name}#${event.channelLeft.name} - ${user.name} left voice channel" }
    logger.debug { "${event.guild.name}#${event.channelJoined.name} - ${user.name} joined voice channel" }

    if (BotUtils.isSelfBot(event.jda, user)) {
      logger.debug { "${event.guild.name}#${event.channelJoined.name} - ${user.name} is self-bot" }
      return
    }

    BotUtils.autoJoin(event.guild, event.channelJoined)
  }

  override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
    if (event.member == null || event.member.user == null)
      return

    val guildId = event.guild.idLong

    val prefix = transaction {
      // HACK: Create settings for a guild that needs to be accessed. This is a problem when restarting bot.
      // TODO: On bot initialization, I should be able to check which Guilds the bot is connected to and purge/add respectively
      val guild = Guild.findById(guildId) ?: Guild.findOrCreate(guildId, event.guild.name, event.guild.region.name)

      guild.settings.prefix
    }

    val rawContent = event.message.contentDisplay
    if (rawContent.startsWith(prefix)) {
      withLoggingContext("guild" to event.guild.name, "text-channel" to event.channel.name) {
        try {
          CommandHandler.handleCommand(event, CommandHandler.parser.parse(prefix, rawContent.toLowerCase()))
        } catch (e: InvalidCommand) {
          val channel = event.channel
          BotUtils.sendMessage(channel, ":no_entry_sign: _Usage: `${e.usage(prefix)}`_")
          logger.warn { "${event.guild.name}#${channel.name}: [$rawContent] ${e.reason}" }
        }

        Guild.updateActivity(event.guild.idLong, event.guild.region.name)
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
    if (BotUtils.isSelfBot(event.jda, event.user)) {
      if (event.guild.audioManager.isConnected) {
        logger.debug {
          "${event.guild}#: Attempting to change nickname from ${event.prevNick} -> ${event.newNick}"
        }

        BotUtils.recordingStatus(event.member, true)
      }
    }
  }

  override fun onReady(event: ReadyEvent) {
    val version = System.getenv("VERSION")
    event
      .jda
      .presence.game = object : Game("$version | https://www.pawa.im", "https://www.pawa.im", Game.GameType.DEFAULT) {

    }

    logger.info { "ONLINE: Connected to ${event.jda.guilds.size} guilds!" }

    // Add guild if not present

    logger.info { "Add any missing Guilds to the Database..." }
    event.jda.guilds.forEach {
      transaction {
        tech.gdragon.db.dao.Guild.findOrCreate(it.idLong, it.name, it.region.name)
      }
    }

    try {
      // TODO Remove this var dubdubdub part we don't ever want to put stuff there
      var dir = Paths.get("/var/www/html/")
      if (Files.notExists(dir)) {
        val dataDirectory = System.getenv("DATA_DIR")
        dir = Files.createDirectories(Paths.get("$dataDirectory/recordings/"))
        logger.info("Creating: " + dir.toString())
      }

      logger.info { "Performing audio file cleanup if necessary..." }
      Files
        .list(dir)
        .filter { path -> Files.isRegularFile(path) && path.toString().toLowerCase().endsWith(".mp3") }
        .forEach { path ->
          try {
            Files.delete(path)
            logger.info { "Deleting file $path..." }
          } catch (e: IOException) {
            logger.error(e) { "Could not delete: $path" }
          }
        }
    } catch (e: IOException) {
      logger.error(e) { "Error preparing to read recordings" }
    }

    //check for servers to join
/*    for (g in event.jda.guilds) {
      val biggest = BotUtils.biggestChannel(g)
      if (biggest != null) {
        BotUtils.joinVoiceChannel(BotUtils.biggestChannel(g), false)
      }
    }*/
  }
}
