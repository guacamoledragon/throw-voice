package tech.gdragon.discord

import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.commands.updateCommands
import dev.minn.jda.ktx.jdabuilder.injectKTX
import mu.KotlinLogging
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent.getKoin
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.audio.Record
import tech.gdragon.commands.audio.Save
import tech.gdragon.commands.audio.Stop
import tech.gdragon.commands.debug.Status
import tech.gdragon.commands.misc.Help
import tech.gdragon.commands.settings.*
import tech.gdragon.commands.slash.Info
import tech.gdragon.commands.slash.Recover
import tech.gdragon.commands.slash.Slash
import tech.gdragon.db.Database
import tech.gdragon.db.dao.Application
import tech.gdragon.db.dao.Guild
import tech.gdragon.listener.EventListener
import tech.gdragon.listener.SystemEventListener
import tech.gdragon.metrics.EventTracer
import javax.security.auth.login.LoginException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import tech.gdragon.i18n.Alias as AliasTranslator
import tech.gdragon.i18n.AutoStop as AutoStopTranslator

class Bot(private val token: String, database: Database) {
  private val logger = KotlinLogging.logger {}
  private val tracer: EventTracer = getKoin().get()

  companion object {
    val PERMISSIONS = listOf(
      Permission.MESSAGE_ATTACH_FILES,
      Permission.MESSAGE_SEND,
      Permission.NICKNAME_CHANGE,
      Permission.USE_APPLICATION_COMMANDS,
      Permission.VIEW_CHANNEL,
      Permission.VOICE_CONNECT,
      Permission.VOICE_SPEAK,
      Permission.VOICE_USE_VAD,
    )
  }

  private lateinit var pawa: Pawa
  lateinit var shardManager: ShardManager

  fun api(): JDA {
    while (!shardManager.statuses.all { it.value == JDA.Status.CONNECTED }) {
      logger.info {
        "Connecting: ${shardManager.statuses.map { "${it.key.shardInfo.shardId}: ${it.value.name}" }.joinToString()}"
      }
      Thread.sleep(1000)
    }

    return shardManager.shards.find { shard -> shard.status == JDA.Status.CONNECTED }!!
  }

  init {
    try {
      val intents = listOf(
        GatewayIntent.GUILD_MESSAGES,
        GatewayIntent.GUILD_VOICE_STATES,
        GatewayIntent.MESSAGE_CONTENT,
      )
      val disabledCache = listOf(
        CacheFlag.ACTIVITY,
        CacheFlag.CLIENT_STATUS,
        CacheFlag.EMOJI,
        CacheFlag.ONLINE_STATUS,
        CacheFlag.SCHEDULED_EVENTS,
        CacheFlag.STICKER,
      )
      // create shard manager
      shardManager = DefaultShardManagerBuilder
        .create(token, intents)
        .disableCache(disabledCache)
        .setChunkingFilter(ChunkingFilter.NONE)
        .setMemberCachePolicy(MemberCachePolicy.VOICE)
        .injectKTX()
        .build()

      // Wait until all shards are connected
      api()
      setActivity()
      addMissingGuilds()
      addMissingApp()

      // Initialize Pawa object
      val standalone = getKoin().getProperty<String>("BOT_STANDALONE").toBoolean()
      pawa = Pawa(api().selfUser.applicationIdLong, database, standalone)

      // Register Listeners
      shardManager.addEventListener(EventListener(pawa), SystemEventListener())
      registerSlashCommands()

      // Update Slash commands
      updateSlashCommands()
    } catch (e: LoginException) {
      logger.error(e) {
        "Could not authenticate using token: $token"
      }
    } catch (e: InterruptedException) {
      logger.error(e) {
        "Interrupted Exception when attempting to create the bot."
      }
    } catch (e: Exception) {
      logger.error(e) {
        "Some shit went really wrong during the bot creation that I had to summon the big papa Exception"
      }
    }
  }

  private fun setActivity() {
    // Set the presence for all shards
    val version: String = getKoin().getProperty("BOT_ACTIVITY", "dev")
    val website: String = getKoin().getProperty("BOT_WEBSITE", "http://localhost:8080/")

    shardManager.shards.forEach { shard ->
      shard.presence.activity = Activity.of(Activity.ActivityType.LISTENING, "$version | $website", website)
    }
  }

  private fun addMissingApp() {
    logger.info { "Add Application to Database..." }
    transaction {
      Application.findOrCreate(api().selfUser.applicationIdLong)
    }
  }

  private fun addMissingGuilds() {
    // Add guild if not present
    logger.info { "Add any missing Guilds to the Database..." }
    shardManager.guilds.forEach {
      transaction {
        Guild.findOrCreate(it.idLong, it.name)
      }
    }

    logger.info { "ONLINE: Connected to ${shardManager.guilds.size} guilds!" }
  }

  private fun registerSlashCommands() {
    shardManager.run {
      onCommand(Alias.command.name) { event ->
        event.guild?.let {
          val translator: AliasTranslator = pawa.translator(it.idLong)

          val commandName = event.getOption("command")?.asString
          val alias = event.getOption("alias")?.asString
          if (commandName != null && alias != null) {
            val command = Command.valueOf(commandName.uppercase())
            pawa
              .createAlias(it.idLong, command, alias)
              ?.let {
                event.reply(":dancers: _${translator.new(alias, commandName)}_").queue()
              }
              ?: event.reply(":no_entry_sign: _${translator.invalid(commandName)}_").queue()
          } else {
            logger.error {
              "No command or alias was provided"
            }
          }
        }
      }

      onCommand(AutoStop.command.name) { event ->
        if (event.isFromGuild) {
          event.guild?.let { guild ->
            val channel = event.getOption("channel")?.asChannel?.asAudioChannel()
            val threshold = event.getOption("threshold")?.asLong ?: 0
            val translator: AutoStopTranslator = pawa.translator(guild.idLong)

            channel?.let {
              pawa.autoStopChannel(it.idLong, it.name, guild.idLong, threshold)

              val replyMessage =
                if (threshold > 0) {
                  ":vibration_mode::wave: _${translator.one(it.id, threshold.toString())}_"
                } else {
                  ":mobile_phone_off::wave: _${translator.some(it.id)}_"
                }

              event.reply(replyMessage).queue()
            } ?: event.reply("Error").queue()
          }
        }
      }

      onCommand(AutoSave.command.name, consumer = AutoSave.slashHandler(pawa))
      onCommand(Ignore.command.name, consumer = Ignore.slashHandler(pawa))
      onCommand(Info.command.name) { event ->
        if (event.isFromGuild) {
          event.guild?.let {
            event
              .replyEmbeds(Info.retrieveInfo(it))
              .queue()
          }
        } else {
          event
            .reply(":no_entry: _Must be in a server to run this command!_")
            .queue()
        }
      }
      onCommand(Language.command.name, consumer = Language.slashHandler(pawa))
      onCommand(Record.command.name, consumer = Record.slashHandler(pawa))
      onCommand(Recover.command.name, consumer = Recover.slashHandler(pawa))
      onCommand(Stop.command.name, consumer = Stop.slashHandler(pawa))
      onCommand(Save.command.name, consumer = Save.slashHandler(pawa))
      onCommand(Volume.command.name, consumer = Volume.slashHandler(pawa))
    }
  }

  private fun updateSlashCommands() {
    api()
      .updateCommands {
        addCommands(
          Alias.command,
          AutoStop.command,
          AutoSave.command,
          Ignore.command,
          Info.command,
          Language.command,
          Record.command,
          Recover.command,
          Stop.command,
          Save.command,
          Volume.command
        )
      }.queue { commands ->
        logger.info {
          commands.joinToString(prefix = "Adding: ") { command -> command.name }.ifEmpty { "No commands!" }
        }
      }
  }

  fun shutdown() {
    shardManager.shutdown()

    shardManager.shards.map { shard ->
      if (!shard.awaitShutdown(10.seconds.toJavaDuration())) {
        shard.shutdownNow()
        shard.awaitShutdown()
      }
    }
  }
}

enum class Command {
  ALIAS {
    override val handler: CommandHandler = Alias()
  },
  AUTORECORD {
    override val handler: CommandHandler = AutoRecord()
  },
  AUTOSAVE {
    override val handler: CommandHandler = AutoSave()
  },
  AUTOSTOP {
    override val handler: CommandHandler = AutoStop()
  },
  HELP {
    override val handler: CommandHandler = Help()
  },
  IGNORE {
    override val handler: CommandHandler = Ignore()
  },
  INFO {
    override val handler: CommandHandler = Info()
  },
  LANG {
    override val handler: CommandHandler = Language()
  },
  PREFIX {
    override val handler: CommandHandler = Prefix()
  },
  RECORD {
    override val handler: CommandHandler = Record()
  },
  REMOVEALIAS {
    override val handler: CommandHandler = RemoveAlias()
  },
  SAVE {
    override val handler: CommandHandler = Save()
  },
  SAVELOCATION {
    override val handler: CommandHandler = SaveLocation()
  },
  SLASH {
    override val handler: CommandHandler = Slash()
  },
  STATUS {
    override val handler: CommandHandler = Status()
  },
  STOP {
    override val handler: CommandHandler = Stop()
  },
  VOLUME {
    override val handler: CommandHandler = Volume()
  };

  abstract val handler: CommandHandler
  // TODO: add a command property here for Slash commands
}
