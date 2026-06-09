package tech.gdragon.commands.audio

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.messages.MessageCreate
import io.github.oshai.kotlinlogging.withLoggingContext
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.discord.message.RecordingStartedReply
import net.dv8tion.jda.api.entities.Guild as DiscordGuild
import tech.gdragon.i18n.Record as RecordTranslator

object Record {
  val command = Command("record", "Start recording voice channel.") {
    option<VoiceChannel>("channel", "Record this channel without joining. _PawaLite only_") {
      setChannelTypes(ChannelType.VOICE, ChannelType.STAGE)
    }
  }

  fun slashHandler(pawa: Pawa): suspend CoroutineEventListener.(GenericCommandInteractionEvent) -> Unit = { event ->
    event.guild?.let {
      val selectedChannel = event.getOption("channel")?.asChannel?.asAudioChannel()
      val voiceChannel: AudioChannel? = if (pawa.isStandalone && selectedChannel != null) {
        selectedChannel
      } else null ?: event.member?.voiceState?.channel

      event.deferReply().await()

      val textChannel = event.messageChannel
      val message = handler(pawa, it, voiceChannel, textChannel)

      BotUtils.reply(event, message)
    }
  }

  fun handler(pawa: Pawa, guild: DiscordGuild, voiceChannel: AudioChannel?, messageChannel: MessageChannel): MessageCreateData {
    val translator: RecordTranslator = pawa.translator(guild.idLong)
    return when {
      voiceChannel == null -> MessageCreate { content = ":no_entry_sign: _${translator.joinChannel}_" }
      guild.audioManager.isConnected -> {
        val connectedVoiceChannel = guild.audioManager.connectedChannel!!
        MessageCreate { content = ":no_entry_sign: _${translator.alreadyInChannel(connectedVoiceChannel.id)}_" }
      }

      else -> {
        withLoggingContext(
          "guild" to guild.name,
          "voice-channel" to voiceChannel.name,
          "text-channel" to messageChannel.name
        ) {
          try {
            val recorder = BotUtils.recordVoiceChannel(voiceChannel, messageChannel)
            pawa.startRecording(recorder.session, guild.idLong)
            val lang = pawa.language(guild.idLong)
            RecordingStartedReply(voiceChannel.id, recorder.session, lang, pawa.recoverEnabled).message
          } catch (e: IllegalArgumentException) {
            val errorMessage = when (e.message) {
              "no-write-permission" ->
                ":no_entry_sign: _Must be able to write in ${messageChannel.asMention}_"

              "no-speak-permission" ->
                ":no_entry_sign: _${translator.cannotRecord(voiceChannel.id, Permission.VOICE_CONNECT.name)}_"

              "no-attach-files-permission" ->
                translator.cannotUpload(messageChannel.id, Permission.MESSAGE_ATTACH_FILES.name)

              "afk-channel" ->
                ":no_entry_sign: _${translator.afkChannel(voiceChannel.id)}_"

              else ->
                ":no_entry_sign: _Unknown bad argument: ${e.message}_"
            }
            MessageCreate { content = errorMessage }
          }
        }
      }
    }
  }
}
