package tech.gdragon.commands.audio

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.commands.settings.Language
import tech.gdragon.i18n.Lang
import tech.gdragon.i18n.Save

class Save : CommandHandler() {
  companion object {
    val translators: MutableMap<Lang, Save> = mutableMapOf()
    fun translator(lang: Lang) = translators.getOrPut(lang) {
      Save(lang)
    }
  }

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    val translator = translator(Language.currentLanguage)
    require(args.size in 0..1) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
    val message =
      if (event.guild.audioManager.connectedChannel == null) {
        ":no_entry_sign: _${translator.notRecording}_"
      } else {
        val voiceChannel = event.guild.audioManager.connectedChannel
        val save = true

        if (args.isEmpty()) {
          BotUtils.leaveVoiceChannel(voiceChannel!!, defaultChannel, save)
          ""
        } else {
          val channelName = if (args.first().startsWith("#")) args.first().substring(1) else args.first()
          val channels = event.guild.getTextChannelsByName(channelName, true)

          if (channels.isEmpty()) {
            ":no_entry_sign: _${translator.channelNotFound(channelName)}_"
          } else {
            channels.forEach {
              BotUtils.leaveVoiceChannel(voiceChannel!!, it, save)
            }
            ""
          }
        }
      }

    if (message.isNotBlank())
      BotUtils.sendMessage(defaultChannel, message)
  }

  override fun usage(prefix: String): String {
    val translator = translator(Language.currentLanguage)
    return translator.usage(prefix)
  }

    override fun description(): String =
      "Saves the current recording and outputs it to the current or specified text channel."
  }
