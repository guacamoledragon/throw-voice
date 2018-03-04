package tech.gdragon.listener

import mu.KotlinLogging
import net.dv8tion.jda.core.entities.Game
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.guild.GuildJoinEvent
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceJoinEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.events.message.priv.PrivateMessageReceivedEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.dao.User
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

    logger.info {"Joined new server '${event.guild.name}', connected to ${event.jda.guilds.size} guilds."}
  }

  override fun onGuildLeave(event: GuildLeaveEvent) {
     transaction {
      val guild = Guild.findById(event.guild.idLong)
      guild?.delete()
    }

    logger.info{"Left server '${event.guild.name}', connected to ${event.jda.guilds.size} guilds."}
  }

  // TODO: Add logging
  override fun onGuildVoiceJoin(event: GuildVoiceJoinEvent) {
    logger.info { "${event.guild.name}#${event.channelJoined.name} - ${event.member.effectiveName} joined voice channel" }
    val audioManager = event.guild.audioManager

    // TODO verify this logic
    val biggestChannel = BotUtils.biggestChannel(event.guild)
    logger.info("${event.guild.name}#${event.channelJoined.name} - ${biggestChannel?.name} is the biggest channel")

    if (biggestChannel != null) {
      BotUtils.joinVoiceChannel(event.channelJoined, false)
    }
  }

/*  override fun onGuildVoiceLeave(event: GuildVoiceLeaveEvent) {
    if (event.member == null || event.member.user == null || event.member.user.isBot)
      return

    val min = transaction {
      val settings = tech.gdragon.db.dao.Guild.findById(event.guild.idLong)!!.settings

      for (channel in settings.channels) {
        if (channel.id.value == event.channelLeft.idLong) {
          return@transaction channel.autoLeave
        }
      }

      Integer.MAX_VALUE
    }

    val size = BotUtils.voiceChannelSize(event.channelLeft)

    val audioManager = event.guild.audioManager

    if (size <= min && audioManager.connectedChannel === event.channelLeft) {
      val autoSave = transaction {
        val settings = tech.gdragon.db.dao.Guild.findById(event.guild.idLong)!!.settings
        settings.autoSave
      }

      if (autoSave) {
        val receiveHandler = audioManager.receiveHandler as CombinedAudioRecorderHandler
        receiveHandler.saveRecording(event.channelLeft, event.member.defaultChannel)
      }

      BotUtils.leaveVoiceChannel(audioManager.connectedChannel)

      val biggest = BotUtils.biggestChannel(event.guild)
      if (biggest != null) {
        BotUtils.joinVoiceChannel(biggest, false)
      }
    }
  }*/

/*  override fun onGuildVoiceMove(event: GuildVoiceMoveEvent) {
    if (event.member == null || event.member.user == null || event.member.user.isBot)
      return

    val audioManager = event.guild.audioManager

    if (audioManager.isConnected) {
      val newSize = BotUtils.voiceChannelSize(event.channelJoined)
      val botSize = BotUtils.voiceChannelSize(audioManager.connectedChannel)

      val min = transaction {
        val settings = tech.gdragon.db.dao.Guild.findById(event.guild.idLong)!!.settings

        for (channel in settings.channels) {
          if (channel.id.value == event.channelJoined.idLong) {
            val autoJoin = channel.autoJoin
            return@transaction autoJoin ?: Integer.MAX_VALUE
          }
        }

        Integer.MAX_VALUE
      }

      if (newSize >= min && botSize < newSize) {  //check for tie with old server
        val autoSave = transaction {
          val settings = Guild.findById(event.guild.idLong)?.settings
          settings?.autoSave
        }

        if (autoSave == true) {
          val receiveHandler = audioManager.receiveHandler as CombinedAudioRecorderHandler
          receiveHandler.saveRecording(event.channelLeft, event.member.defaultChannel)
        }

        BotUtils.joinVoiceChannel(event.channelJoined, false)
      }

    } else {
      val biggestChannel = BotUtils.biggestChannel(event.guild)
      if (biggestChannel != null) {
        BotUtils.joinVoiceChannel(biggestChannel, false)
      }
    }

    //Check if bot needs to leave old channel
    val min = transaction {
      val settings = tech.gdragon.db.dao.Guild.findById(event.guild.idLong)!!.settings

      for (channel in settings.channels) {
        if (channel.id.value == event.channelJoined.idLong) {
          return@transaction channel.autoLeave
        }
      }

      0 // TODO, weeeeird, fix these loops
    }
    val size = BotUtils.voiceChannelSize(event.channelLeft)

    if (audioManager.isConnected && size <= min && audioManager.connectedChannel === event.channelLeft) {
      val autoSave = transaction {
        val settings = Guild.findById(event.guild.idLong)?.settings
        settings?.autoSave
      }

      if (autoSave == true) {
        val receiveHandler = audioManager.receiveHandler as CombinedAudioRecorderHandler
        receiveHandler.saveRecording(event.channelLeft, event.member.defaultChannel)
      }

      BotUtils.leaveVoiceChannel(audioManager.connectedChannel)

      val biggest = BotUtils.biggestChannel(event.guild)
      if (biggest != null) {
        BotUtils.joinVoiceChannel(event.channelJoined, false)
      }
    }
  }*/

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
      if (message.endsWith("off")) {
        for (g in event.jda.guilds) {
          if (g.getMember(event.author) != null) {
            transaction {
              val settings = tech.gdragon.db.dao.Guild.findById(g.idLong)!!.settings
              User.findOrCreate(event.author.idLong, event.author.name, settings)
            }
          }
        }
        event.channel.sendMessage("Alerts now off, message `!alerts on` to re-enable at any time").queue()

      } else if (message.endsWith("on")) {
        for (g in event.jda.guilds) {
          if (g.getMember(event.author) != null) {
            transaction {
              val settings = tech.gdragon.db.dao.Guild.findById(g.idLong)!!.settings
              val user = User.findOrCreate(event.author.idLong, event.author.name, settings)
              user.delete()
              user
            }
          }
        }
        event.channel.sendMessage("Alerts now on, message `!alerts off` to disable at any time").queue()
      } else {
        event.channel.sendMessage("!alerts [on | off]").queue()
      }
    } else {
      event.channel.sendMessage("DM commands unsupported, send `!help` in your guild chat for more info.").queue()
    }
  }

  override fun onReady(event: ReadyEvent) {
    event
      .jda
      .presence.game = object : Game("1.0.0 | https://www.pawabot.site", "http://pawabot.site", Game.GameType.DEFAULT) {

    }

    logger.info{"ONLINE: Connected to ${event.jda.guilds.size} guilds!"}

    // Add guild if not present
    for (g in event.jda.guilds) {
      tech.gdragon.db.dao.Guild.findOrCreate(g.idLong, g.name)
    }

    try {
      // TODO Remove this var dubdubdub part we don't ever want to put stuff there
      var dir = Paths.get("/var/www/html/")
      if (Files.notExists(dir)) {
        val dataDirectory = System.getenv("DATA_DIR")
        dir = Files.createDirectories(Paths.get(dataDirectory + "/recordings/"))
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

