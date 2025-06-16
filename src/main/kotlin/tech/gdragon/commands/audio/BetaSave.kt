package tech.gdragon.commands.audio

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.messages.reply_
import dev.minn.jda.ktx.messages.send
import io.github.oshai.kotlinlogging.KotlinLogging
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.managers.AudioManager
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.discord.message.RecordingReply
import tech.gdragon.listener.SharedAudioRecorder
import tech.gdragon.listener.StandaloneAudioRecorder

object BetaSave {
  val logger = KotlinLogging.logger { }
  val command = Command("beta-save", "save")

  fun handler(pawa: Pawa): suspend CoroutineEventListener.(GenericCommandInteractionEvent) -> Unit = { event ->
    val audioManager: AudioManager = event.guild!!.audioManager
    if (!audioManager.isConnected) {
      event.reply_("Not connected, shoo!").await()
    } else {
      val voiceChannel = audioManager.connectedChannel!!
      val messageChannel = event.messageChannel

      val recorder = if (pawa.isStandalone)
        audioManager.receivingHandler as StandaloneAudioRecorder
      else
        audioManager.receivingHandler as SharedAudioRecorder

      audioManager.closeAudioConnection()
      val (recording, lock) = recorder.saveRecording(voiceChannel, messageChannel)
      recorder.disconnect(lock)

      if (recording != null) {
        val reply = RecordingReply(recording, pawa.config.appUrl)
        if (event.isAcknowledged)
          event.hook.send(embeds = listOf(reply.embed), components = listOf(reply.component)).await()
        else
          event.reply(reply.message).await()
      }
    }
  }
}
