package tech.gdragon.commands.audio

import dev.minn.jda.ktx.CoroutineEventListener
import dev.minn.jda.ktx.interactions.Command
import dev.minn.jda.ktx.interactions.option
import mu.withLoggingContext
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.ChannelType
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.VoiceChannel
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang
import net.dv8tion.jda.api.entities.Guild as DiscordGuild
import tech.gdragon.i18n.Record as RecordTranslator

class Record : CommandHandler() {
  companion object {
    val command = Command("record", "Start recording voice channel.") {
      option<VoiceChannel>("channel", "Record this channel without joining. _PawaLite only_") {
        setChannelTypes(ChannelType.VOICE, ChannelType.STAGE)
      }
    }

    fun slashHandler(pawa: Pawa): suspend CoroutineEventListener.(SlashCommandEvent) -> Unit = { event ->
      tracer.sendEvent(mapOf("command" to command.name))

      event.guild?.let {
        val selectedChannel = event.getOption("channel")?.asGuildChannel as VoiceChannel?
        val voiceChannel: VoiceChannel? = if (pawa.isStandalone && selectedChannel != null) {
          selectedChannel
        } else null ?: event.member?.voiceState?.channel

        val textChannel = try {
          event.textChannel
        } catch (_: Exception) {
          // This will happen if the event is triggered from a Voice Channel chat
          // Source: https://support.discord.com/hc/en-us/articles/4412085582359-Text-Channels-Text-Chat-In-Voice-Channels#h_01FMJT3SP072ZFJCZWR0EW6CJ1
          BotUtils.defaultTextChannel(it) ?: BotUtils.findPublicChannel(it)
        }

        val message = handler(pawa, it, voiceChannel, textChannel)
        event.reply(message).queue()
      }
    }

    fun handler(pawa: Pawa, guild: DiscordGuild, voiceChannel: VoiceChannel?, textChannel: TextChannel?): String {
      val translator: RecordTranslator = pawa.translator(guild.idLong)
      return when {
        voiceChannel == null -> ":no_entry_sign: _${translator.joinChannel}_"
        guild.audioManager.isConnected -> {
          val connectedVoiceChannel = guild.audioManager.connectedChannel!!
          ":no_entry_sign: _${translator.alreadyInChannel(connectedVoiceChannel.id)}_"
        }

        else -> {
          withLoggingContext("guild" to guild.name, "voice-channel" to voiceChannel.name, "text-channel" to textChannel?.name) {
            try {
              val recorder = BotUtils.recordVoiceChannel(voiceChannel, textChannel)
              pawa.startRecording(recorder.session, guild.idLong)
              translator.recording(voiceChannel.id, recorder.session)
            } catch (e: IllegalArgumentException) {
              when (e.message) {
                "no-write-permission" ->
                  "Attempted to record, but bot cannot write to any channel."

                "no-speak-permission" ->
                  ":no_entry_sign: _${translator.cannotRecord(voiceChannel.id, Permission.VOICE_CONNECT.name)}_"

                "no-attach-files-permission" ->
                  translator.cannotUpload(textChannel!!.id, Permission.MESSAGE_ATTACH_FILES.name)

                "afk-channel" ->
                  ":no_entry_sign: _${translator.afkChannel(voiceChannel.id)}_"

                else ->
                  ":no_entry_sign: _Unknown bad argument: ${e.message}_"
              }
            }
          }
        }
      }
    }
  }

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent, pawa: Pawa) {
    require(pawa.isStandalone || args.isEmpty()) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val voiceChannel: VoiceChannel? = if (pawa.isStandalone && args.isNotEmpty()) {
      val channelName = args.joinToString(separator = " ")
      event.jda
        .getVoiceChannelsByName(channelName, false)
        .firstOrNull()
    } else null ?: event.member?.voiceState?.channel

    val message = handler(pawa, event.guild, voiceChannel, event.channel)
    BotUtils.sendMessage(event.channel, message)
  }

  override fun usage(prefix: String, lang: Lang): String {
    val translator = Babel.record(lang)
    return translator.usage(prefix)
  }

  override fun description(lang: Lang): String = "Ask the bot to join and record in your current channel."
}
