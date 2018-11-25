package tech.gdragon.discord

import mu.KotlinLogging
import net.dv8tion.jda.core.AccountType
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.Permission
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.audio.Clip
import tech.gdragon.commands.audio.Save
import tech.gdragon.commands.misc.Help
import tech.gdragon.commands.misc.Join
import tech.gdragon.commands.misc.Leave
import tech.gdragon.commands.settings.*
import tech.gdragon.listener.EventListener
import javax.security.auth.login.LoginException

class Bot(config: BotConfig) {
  private val logger = KotlinLogging.logger {}

  companion object {
    val PERMISSIONS = listOf(
      Permission.MESSAGE_READ,
      Permission.MESSAGE_WRITE,
      Permission.VOICE_CONNECT,
      Permission.VOICE_USE_VAD,
      Permission.VOICE_SPEAK,
      Permission.NICKNAME_CHANGE
    )
  }

  lateinit var api: JDA

  init {
    try {
      //create bot instance
      api = JDABuilder(AccountType.BOT)
        .setToken(config.token)
        .addEventListener(EventListener())
        .build()
        .awaitReady()
    } catch (e: LoginException) {
      logger.error(e) {
        "Could not authenticate using token: ${config.token}"
      }
    } catch (e: InterruptedException) {
      logger.error(e) {
        "Interrupted Exception when attempting to create bot."
      }
    } catch (e: Exception) {
      logger.error(e) {
        "Some shit went really wrong during the bot creation that I had to summon the big papa Exception"
      }
    }
  }
}

data class BotConfig(val token: String)

enum class Command {
  ALIAS {
    override val handler: CommandHandler = Alias()
  },
  AUTOJOIN {
    override val handler: CommandHandler = AutoJoin()
  },
  AUTOLEAVE {
    override val handler: CommandHandler = AutoLeave()
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
  JOIN {
    override val handler: CommandHandler = Join()
  },
  LEAVE {
    override val handler: CommandHandler = Leave()
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


