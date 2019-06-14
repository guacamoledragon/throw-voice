package tech.gdragon.discord

import mu.KotlinLogging
import net.dv8tion.jda.core.AccountType
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.Permission
import org.koin.core.KoinComponent
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.audio.Clip
import tech.gdragon.commands.audio.Save
import tech.gdragon.commands.misc.Help
import tech.gdragon.commands.misc.Record
import tech.gdragon.commands.misc.Stop
import tech.gdragon.commands.settings.*
import tech.gdragon.listener.EventListener
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

  lateinit var api: JDA

  init {
    try {
      //create bot instance
      api = JDABuilder(AccountType.BOT)
        .setToken(token)
        .addEventListener(EventListener())
        .build()
        .awaitReady()
    } catch (e: LoginException) {
      logger.error(e) {
        "Could not authenticate using token: $token"
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
