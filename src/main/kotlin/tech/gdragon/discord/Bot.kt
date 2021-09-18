package tech.gdragon.discord

import dev.minn.jda.ktx.injectKTX
import mu.KotlinLogging
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder
import net.dv8tion.jda.api.sharding.ShardManager
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.koin.core.component.KoinComponent
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.audio.Clip
import tech.gdragon.commands.audio.Save
import tech.gdragon.commands.debug.Status
import tech.gdragon.commands.misc.Help
import tech.gdragon.commands.misc.Record
import tech.gdragon.commands.misc.Stop
import tech.gdragon.commands.settings.*
import tech.gdragon.commands.slash.registerSlashCommands
import tech.gdragon.db.Database
import tech.gdragon.listener.EventListener
import tech.gdragon.listener.SystemEventListener
import javax.security.auth.login.LoginException

class Bot(private val token: String, database: Database) {
  private val logger = KotlinLogging.logger {}

  companion object {
    val PERMISSIONS = listOf(
      Permission.MESSAGE_ATTACH_FILES,
      Permission.MESSAGE_READ,
      Permission.MESSAGE_WRITE,
      Permission.NICKNAME_CHANGE,
      Permission.VOICE_CONNECT,
      Permission.VOICE_SPEAK,
      Permission.VOICE_USE_VAD,
      Permission.USE_SLASH_COMMANDS
    )
  }

  lateinit var shardManager: ShardManager

  fun api(): JDA {
    while (!shardManager.statuses.all { it.value == JDA.Status.CONNECTED }) {
      Thread.sleep(500)
    }

    return shardManager.shards.find { shard -> shard.status == JDA.Status.CONNECTED }!!
  }

  init {
    try {
      // create shard manager
      shardManager = DefaultShardManagerBuilder
        .create(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_VOICE_STATES)
        .disableCache(CacheFlag.ACTIVITY, CacheFlag.EMOTE, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS)
        .setChunkingFilter(ChunkingFilter.NONE)
        .setMemberCachePolicy(MemberCachePolicy.VOICE)
        .addEventListeners(EventListener(), SystemEventListener())
        .injectKTX()
        .build()
      registerSlashCommands(shardManager)
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

  fun shutdown() {
    shardManager.shutdown()
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
  CLIP {
    override val handler: CommandHandler = Clip()
  },
  HELP {
    override val handler: CommandHandler = Help()
  },
  IGNORE {
    override val handler: CommandHandler = Ignore()
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
}
