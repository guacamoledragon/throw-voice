package tech.gdragon.commands.audio

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.managers.AudioManager
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.listener.SharedAudioRecorder
import tech.gdragon.listener.StandaloneAudioRecorder

object BetaRecord {
  val command = Command("record", "Start recording voice channel.")

  fun handler(pawa: Pawa): suspend CoroutineEventListener.(GenericCommandInteractionEvent) -> Unit = { event ->
    val textChannel = event.messageChannel
    val voiceChannel: AudioChannel? = event.member?.voiceState?.channel
    val audioManager: AudioManager = event.guild!!.audioManager

    if (audioManager.isConnected || voiceChannel == null) {
      event.reply_("join voice channel before starting recording").await()
    } else {
      event.deferReply().await()

      audioManager.openAudioConnection(voiceChannel)
      val recorder = if (pawa.isStandalone)
        StandaloneAudioRecorder(1.0, voiceChannel, textChannel)
      else
        SharedAudioRecorder(1.0, voiceChannel, textChannel)

      audioManager.receivingHandler = recorder
      event.hook.sendMessage("Recording Started!`${recorder.session}`").await()
    }
  }
}
