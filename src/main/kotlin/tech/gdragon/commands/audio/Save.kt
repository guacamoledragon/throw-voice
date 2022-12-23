package tech.gdragon.commands.audio

import dev.minn.jda.ktx.CoroutineEventListener
import dev.minn.jda.ktx.interactions.Command
import dev.minn.jda.ktx.interactions.option
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang
import net.dv8tion.jda.api.entities.Guild as DiscordGuild
import tech.gdragon.i18n.Save as SaveTranslator

class Save : CommandHandler() {
  companion object {
    val command = Command("save", "Stop and save current recording.") {
      option<TextChannel>("channel", "Upload the recording to this channel.") {
        setChannelTypes(ChannelType.TEXT)
      }
    }

    fun handler(pawa: Pawa, guild: DiscordGuild, textChannel: TextChannel): String? {
      val translator: SaveTranslator = pawa.translator(guild.idLong)
      return if (!guild.audioManager.isConnected)
        ":no_entry_sign: _${translator.notRecording}_"
      else {
        val voiceChannel = guild.audioManager.connectedChannel!!
        BotUtils.leaveVoiceChannel(voiceChannel, textChannel, save = true)
        null
      }
    }

    fun slashHandler(pawa: Pawa): suspend CoroutineEventListener.(SlashCommandEvent) -> Unit = { event ->
      tracer.sendEvent(mapOf("command" to command.name))
      event.guild?.let {
        val channel = try {
          val guildChannel = event.getOption("channel")?.asGuildChannel as TextChannel? ?: event.textChannel
          require(guildChannel.canTalk())
          guildChannel
        } catch (_: Exception) {
          // This will happen if the event is triggered from a Voice Channel chat
          // Source: https://support.discord.com/hc/en-us/articles/4412085582359-Text-Channels-Text-Chat-In-Voice-Channels#h_01FMJT3SP072ZFJCZWR0EW6CJ1
          BotUtils.defaultTextChannel(it) ?: BotUtils.findPublicChannel(it)
        }

        handler(pawa, it, channel!!)?.let { message ->
          event.reply(message).queue()
        } ?: event.reply("✅").queue()
      }

    }
  }

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent, pawa: Pawa) {
    require(args.size in 0..1) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val translator: SaveTranslator = pawa.translator(event.guild.idLong)
    val message = if (args.isEmpty()) {
      handler(pawa, event.guild, event.channel)
    } else {
      val channelName = if (args.first().startsWith("#")) args.first().substring(1) else args.first()
      val channels = event.guild.getTextChannelsByName(channelName, true)

      if (channels.isEmpty()) {
        ":no_entry_sign: _${translator.channelNotFound(channelName)}_"
      } else {
        handler(pawa, event.guild, channels.first())
      }
    }

    message?.let {
      BotUtils.sendMessage(event.channel, it)
    }
  }

  override fun usage(prefix: String, lang: Lang): String {
    val translator = Babel.save(lang)
    return translator.usage(prefix)
  }

  override fun description(lang: Lang): String {
    val translator = Babel.save(lang)
    return translator.description
  }
}
