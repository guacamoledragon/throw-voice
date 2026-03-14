package tech.gdragon.commands.audio

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.messages.MessageCreate
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.discord.message.RecordingReply
import tech.gdragon.listener.AudioRecorder
import net.dv8tion.jda.api.entities.Guild as DiscordGuild
import tech.gdragon.i18n.Save as SaveTranslator

object Save {
  val command = Command("save", "Stop and save current recording.") {
    option<TextChannel>("channel", "Upload the recording to this channel.") {
      setChannelTypes(ChannelType.TEXT)
    }
  }

  fun handler(pawa: Pawa, guild: DiscordGuild, messageChannel: MessageChannel): String? {
    val translator: SaveTranslator = pawa.translator(guild.idLong)
    return if (!guild.audioManager.isConnected)
      ":no_entry_sign: _${translator.notRecording}_"
    else {
      val voiceChannel = guild.audioManager.connectedChannel!!

      val guildChannel = if(messageChannel.canTalk()) {
        messageChannel
      } else {
        voiceChannel.asGuildMessageChannel()
      }

      BotUtils.leaveVoiceChannel(voiceChannel, guildChannel, save = true)
      null
    }
  }

  fun slashHandler(pawa: Pawa): suspend CoroutineEventListener.(GenericCommandInteractionEvent) -> Unit = { event ->
    event.guild?.let {
      val messageChannel: MessageChannel =
        event.getOption("channel")?.asChannel?.asGuildMessageChannel() ?: event.messageChannel

      event.deferReply().await()

      val message = handler(pawa, it, messageChannel)
      if (message != null) {
        BotUtils.reply(event, MessageCreate(message))
      } else {
        val recorder = event.guild?.audioManager?.receivingHandler as? AudioRecorder
        val recording = recorder?.recording!!
        val recordingEmbed = RecordingReply(recording, pawa.config.appUrl)
        BotUtils.reply(event, recordingEmbed.message)
      }
    }
  }
}
