package tech.gdragon.commands.audio

import dev.minn.jda.ktx.CoroutineEventListener
import dev.minn.jda.ktx.interactions.Command
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang
import net.dv8tion.jda.api.entities.Guild as DiscordGuild
import tech.gdragon.i18n.Stop as StopTranslator

class Stop : CommandHandler() {
  companion object {
    val command = Command("stop", "Stop recording voice channel.")

    fun handler(pawa: Pawa, guild: DiscordGuild, textChannel: TextChannel?): String {
      val translator: StopTranslator = pawa.translator(guild.idLong)
      return if (guild.audioManager.isConnected) {
        val voiceChannel = guild.audioManager.connectedChannel!!
        val save = pawa.autoSave(guild.idLong)
        val recorder = BotUtils.leaveVoiceChannel(voiceChannel, textChannel, save)
        pawa.stopRecording(recorder.session)
        ":wave: _${translator.leaveChannel(voiceChannel.id)}_"
      } else {
        ":no_entry_sign: _${translator.noChannel}_"
      }
    }

    fun slashHandler(pawa: Pawa): suspend CoroutineEventListener.(SlashCommandEvent) -> Unit = { event ->
      tracer.sendEvent(mapOf("command" to command.name))

      event.guild?.let {
        val textChannel = try {
          event.textChannel
        } catch (_: Exception) {
          // This will happen if the event is triggered from a Voice Channel chat
          // Source: https://support.discord.com/hc/en-us/articles/4412085582359-Text-Channels-Text-Chat-In-Voice-Channels#h_01FMJT3SP072ZFJCZWR0EW6CJ1
          BotUtils.defaultTextChannel(it) ?: BotUtils.findPublicChannel(it)
        }

        val message = handler(pawa, it, textChannel)
        event.reply(message).queue()
      }
    }
  }

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent, pawa: Pawa) {
    require(args.isEmpty()) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val message = handler(pawa, event.guild, event.channel)
    BotUtils.sendMessage(event.channel, message)
  }

  override fun usage(prefix: String, lang: Lang): String {
    val translator = Babel.stop(lang)
    return translator.usage(prefix)
  }

  override fun description(lang: Lang): String = "Ask the bot to stop recording and leave its current channel"
}
