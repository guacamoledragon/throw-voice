package tech.gdragon.discord

import mu.KotlinLogging
import net.dv8tion.jda.core.AccountType
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.Permission
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.audio.Clip
import tech.gdragon.commands.audio.Echo
import tech.gdragon.commands.audio.MessageInABottle
import tech.gdragon.commands.audio.Save
import tech.gdragon.commands.misc.Help
import tech.gdragon.commands.misc.Join
import tech.gdragon.commands.misc.Leave
import tech.gdragon.commands.settings.*
import tech.gdragon.listener.EventListener
import javax.security.auth.login.LoginException

class Bot(token: String) {
  private val logger = KotlinLogging.logger {}
  companion object {
    val PERMISSIONS = listOf(
      Permission.MESSAGE_READ,
      Permission.MESSAGE_WRITE,
      Permission.VOICE_CONNECT,
      Permission.VOICE_USE_VAD,
      Permission.VOICE_SPEAK
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

      // Register misc commands
      CommandHandler.commands["help"] = Help()
      CommandHandler.commands["join"] = Join()
      CommandHandler.commands["leave"] = Leave()

      // Register audio commands
      CommandHandler.commands["clip"] = Clip()
      CommandHandler.commands["echo"] = Echo()
      CommandHandler.commands["miab"] = MessageInABottle()
      CommandHandler.commands["save"] = Save()

      // Register settings commands
      CommandHandler.commands["alias"] = Alias()
      CommandHandler.commands["alerts"] = Alerts()
      CommandHandler.commands["autojoin"] = AutoJoin()
      CommandHandler.commands["autoleave"] = AutoLeave()
      CommandHandler.commands["autosave"] = AutoSave()
      CommandHandler.commands["prefix"] = Prefix()
      CommandHandler.commands["removealias"] = RemoveAlias()
      CommandHandler.commands["savelocation"] = SaveLocation()
      CommandHandler.commands["volume"] = Volume()
    } catch (e: LoginException) {
      logger.error(e) {
        "Could not authenticate using token: $token"
      }
      e.printStackTrace()
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
