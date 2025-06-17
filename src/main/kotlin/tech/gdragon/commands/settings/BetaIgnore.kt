package tech.gdragon.commands.settings

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.components.getOption
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.managers.AudioManager
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.listener.SharedAudioRecorder
import tech.gdragon.listener.StandaloneAudioRecorder

object BetaIgnore {
  val command = Command("ignore", "Ignore audio from specified during User for current recording.") {
    option<User>("user", "The user to ignore", true)
  }

  fun handler(pawa: Pawa): suspend CoroutineEventListener.(GenericCommandInteractionEvent) -> Unit = { event ->
    val audioManager: AudioManager = event.guild!!.audioManager

    if (!audioManager.isConnected) {
      event.reply_("Not connected, shoo!").await()
    } else {
      val ignoreUser = event.getOption<User>("user")!!.idLong

      val recorder = if (pawa.isStandalone)
        audioManager.receivingHandler as StandaloneAudioRecorder
      else
        audioManager.receivingHandler as SharedAudioRecorder

      pawa.ignoreUsers(recorder.session, listOf(ignoreUser))
      recorder.silenceUser(ignoreUser)

      event.reply_("Ignoring user: <@$ignoreUser>").await()
    }
  }
}
