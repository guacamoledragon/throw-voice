package tech.gdragon.commands.audio

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.messages.MessageCreate
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import net.dv8tion.jda.api.entities.Guild as DiscordGuild
import tech.gdragon.i18n.Stop as StopTranslator

object Stop {
  val command = Command("stop", "Stop recording voice channel.")

  fun handler(pawa: Pawa, guild: DiscordGuild, messageChannel: MessageChannel): String {
    val translator: StopTranslator = pawa.translator(guild.idLong)
    return if (guild.audioManager.isConnected) {
      val audioChannel = guild.audioManager.connectedChannel!!
      val save = pawa.autoSave(guild.idLong)

      val guildChannel = if (messageChannel.canTalk()) {
        messageChannel
      } else {
        audioChannel.asGuildMessageChannel()
      }

      val recorder = BotUtils.leaveVoiceChannel(audioChannel, guildChannel, save)

      pawa.stopRecording(recorder.session)
      ":wave: _${translator.leaveChannel(audioChannel.id)}._"
    } else {
      ":no_entry_sign: _${translator.noChannel}_"
    }
  }

  fun slashHandler(pawa: Pawa): suspend CoroutineEventListener.(GenericCommandInteractionEvent) -> Unit = { event ->
    event.guild?.let {
      event.deferReply().await()
      val message = handler(pawa, it, event.messageChannel)
      BotUtils.reply(event, MessageCreate(message))
    }
  }
}
