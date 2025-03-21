package tech.gdragon.commands.audio

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.CoroutineEventListener
import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.messages.MessageCreate
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
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

  override fun action(args: Array<String>, event: MessageReceivedEvent, pawa: Pawa) {
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
