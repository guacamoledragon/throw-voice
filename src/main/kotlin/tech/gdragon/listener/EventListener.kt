package tech.gdragon.listener

import mu.KotlinLogging
import net.dv8tion.jda.core.entities.Game
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.guild.GuildJoinEvent
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent
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
import tech.gdragon.commands.settings.configureAlerts
import tech.gdragon.db.dao.Guild
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import net.dv8tion.jda.core.entities.Guild as DiscordGuild

class EventListener : ListenerAdapter() {

  private val logger = KotlinLogging.logger {}

  override fun onGuildJoin(event: GuildJoinEvent) {
    transaction {
      val guild = event.guild
      Guild.findOrCreate(guild.idLong, guild.name)
    }

    logger.info { "Joined new server '${event.guild.name}', connected to ${event.jda.guilds.size} guilds." }
  }

  override fun onGuildLeave(event: GuildLeaveEvent) {
    transaction {
      val guild = Guild.findById(event.guild.idLong)
      guild?.delete()
    }

    logger.info { "Left server '${event.guild.name}', connected to ${event.jda.guilds.size} guilds." }
  }

  override fun onGuildVoiceJoin(event: GuildVoiceJoinEvent) {
    val user = event.member.user
    logger.debug { "${event.guild.name}#${event.channelJoined.name} - ${user.name} joined voice channel" }

    if (BotUtils.isSelfBot(event.jda, user)) {
      logger.debug { "${event.guild.name}#${event.channelJoined.name} - ${user.name} is self-bot" }
      return
    }

    val errorMessage = BotUtils.autoJoin(event.guild, event.channelJoined) { ex ->
      """|:no_entry_sign: _Cannot join **<#${event.channelJoined.id}>** on Guild **${event.guild.name}**,
         |need permission:_ ```${ex.permission}```
         |""".trimMargin()
    }
    errorMessage?.let { /*BotUtils.alert(event.channelJoined, it)*/ }
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

    val errorMessage = BotUtils.autoJoin(event.guild, event.channelJoined) { ex ->
      """|:no_entry_sign: _Cannot join **<#${event.channelJoined.id}>** on Guild **${event.guild.name}**,
         |need permission:_ ```${ex.permission}```
         |""".trimMargin()
    }
    errorMessage?.let { /*BotUtils.alert(event.channelJoined, it)*/ }
  }

  override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
    if (event.member == null || event.member.user == null || event.member.user.isBot)
      return

    val guildId = event.guild.idLong

    val prefix = transaction {
      // HACK: Create settings for a guild that needs to be accessed. This is a problem when restarting bot.
      // TODO: On bot initialization, I should be able to check which Guilds the bot is connected to and purge/add respectively
      val guild = Guild.findById(guildId) ?: Guild.findOrCreate(guildId, event.guild.name)

      guild.settings.prefix
    }

    val rawContent = event.message.contentDisplay
    if (rawContent.startsWith(prefix)) {
      try {
        CommandHandler.handleCommand(event, CommandHandler.parser.parse(prefix, rawContent.toLowerCase()))
      } catch (e: InvalidCommand) {
        val channel = event.channel
        BotUtils.sendMessage(channel, ":no_entry_sign: _Usage: `${e.usage(prefix)}`_")
        logger.warn { "${event.guild.name}#${channel.name}: [$rawContent] ${e.reason}" }
      }
    }
  }

  override fun onPrivateMessageReceived(event: PrivateMessageReceivedEvent) {
    if (event.author == null || event.author.isBot)
      return

    val message = event.message.contentDisplay

    if (message.startsWith("!alerts")) {
      val userId = event.author.id

      event.jda.guilds
        .filter { it.isMember(event.author) }
        .forEach {
          val guildId = it.idLong
          val alert = { enable: Boolean -> configureAlerts(userId, guildId, enable) }

          when {
            message.endsWith("off") -> {
              alert(false)
              event.channel.sendMessage("Alerts now off for guild `${it.name}`, message `!alerts on` to re-enable.").queue()
            }
            message.endsWith("on") -> {
              alert(true)
              event.channel.sendMessage("Alerts now on for guild `${it.name}`, message `!alerts off` to disable.").queue()
            }
            else -> event.channel.sendMessage("!alerts [on | off]").queue()
          }
        }
    } else {
      event.channel.sendMessage("The only DM command supported is `!alerts`.").queue()
    }
  }

  override fun onReady(event: ReadyEvent) {
    event
      .jda
      .presence.game = object : Game("1.2.0-beta.76 | https://www.pawa.im", "https://www.pawa.im", Game.GameType.DEFAULT) {

    }

    logger.info { "ONLINE: Connected to ${event.jda.guilds.size} guilds!" }

    // Add guild if not present
    for (g in event.jda.guilds) {
      tech.gdragon.db.dao.Guild.findOrCreate(g.idLong, g.name)
    }

    try {
      // TODO Remove this var dubdubdub part we don't ever want to put stuff there
      var dir = Paths.get("/var/www/html/")
      if (Files.notExists(dir)) {
        val dataDirectory = System.getenv("DATA_DIR")
        dir = Files.createDirectories(Paths.get("$dataDirectory/recordings/"))
        logger.info("Creating: " + dir.toString())
      }

      Files
        .list(dir)
        .filter { path -> Files.isRegularFile(path) && path.toString().toLowerCase().endsWith(".mp3") }
        .forEach { path ->
          try {
            Files.delete(path)
            logger.info("Deleting file $path...")
          } catch (e1: IOException) {
            logger.error("Could not delete: " + path, e1)
          }
        }
    } catch (e1: IOException) {
      logger.error("Error preparing to read recordings", e1)
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

