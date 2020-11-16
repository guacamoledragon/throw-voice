package tech.gdragon.discord

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
import tech.gdragon.commands.misc.Help
import tech.gdragon.commands.misc.Record
import tech.gdragon.commands.misc.Stop
import tech.gdragon.commands.settings.*
import tech.gdragon.listener.EventListener
import tech.gdragon.listener.SystemEventListener
import javax.security.auth.login.LoginException

class Bot : KoinComponent {
  private val token: String = getKoin().getProperty("BOT_TOKEN", "")
  private val logger = KotlinLogging.logger {}

  companion object {
    val PERMISSIONS = listOf(
      Permission.MESSAGE_ATTACH_FILES,
      Permission.MESSAGE_READ,
      Permission.MESSAGE_WRITE,
      Permission.NICKNAME_CHANGE,
      Permission.VOICE_CONNECT,
      Permission.VOICE_SPEAK,
      Permission.VOICE_USE_VAD
    )
  }

  lateinit var shardManager: ShardManager

  fun api(): JDA {
    while (!shardManager.statuses.any { it.value == JDA.Status.CONNECTED }) {
      Thread.sleep(50)
    }

    return shardManager.shards.find { shard -> shard.status == JDA.Status.CONNECTED }!!
  }

  init {
    try {
      // create shard manager
      shardManager = DefaultShardManagerBuilder
        .create(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_VOICE_STATES)
        .disableCache(CacheFlag.ACTIVITY, CacheFlag.EMOTE, CacheFlag.CLIENT_STATUS)
        .setChunkingFilter(ChunkingFilter.NONE)
        .setMemberCachePolicy(MemberCachePolicy.VOICE)
        .addEventListeners(EventListener(), SystemEventListener())
        .build()
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
}

enum class Command {
  ALIAS {
    override val handler: CommandHandler = Alias()
  },
  AUTORECORD {
    override val handler: CommandHandler = AutoRecord()
  },
  AUTOSTOP {
    override val handler: CommandHandler = AutoStop()
  },
  AUTOSAVE {
    override val handler: CommandHandler = AutoSave()
  },
  CLIP {
    override val handler: CommandHandler = Clip()
  },
  HELP {
    override val handler: CommandHandler = Help()
  },
  RECORD {
    override val handler: CommandHandler = Record()
  },
  STOP {
    override val handler: CommandHandler = Stop()
  },
  PREFIX {
    override val handler: CommandHandler = Prefix()
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
  VOLUME {
    override val handler: CommandHandler = Volume()
  };

  abstract val handler: CommandHandler
}
