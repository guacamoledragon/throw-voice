package tech.gdragon.commands.settings

import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.interactions.commands.choice
import dev.minn.jda.ktx.interactions.commands.option
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.discord.Command
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang
import tech.gdragon.i18n.Alias as AliasTranslator

class Alias : CommandHandler() {
  companion object {
    val command by lazy {
      Command("alias", "Creates an alias, or alternate name, to a command for customization.") {
        option<String>("command", "The built-in command you want to alias.", true) {
          Command
            .values()
            .map{ it.name.lowercase() }
            .filterNot { it == "alias" }
            .forEach {
              choice(it,it)
            }
        }
        option<String>("alias", "The alias you want to use.", true)
      }
    }
  }

  override fun action(args: Array<String>, event: MessageReceivedEvent, pawa: Pawa) {
    require(args.size == 2) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
    val command = args.first().uppercase()
    val translator: AliasTranslator = pawa.translator(event.guild.idLong)

    // Checks that command to alias exists
    if (Command.ALIAS.name == command || Command.values().none { it.name == command }) {
      BotUtils.sendMessage(defaultChannel, ":no_entry_sign: _${translator.invalid(command.lowercase())}_")
    } else {
      val alias = args[1]

      pawa
        .createAlias(event.guild.idLong, Command.valueOf(command), alias)
        ?.let {
          BotUtils.sendMessage(defaultChannel, ":dancers: _${translator.new(alias, it.name.lowercase())}_")
        }
        ?: BotUtils.sendMessage(defaultChannel, ":no_entry_sign: _${translator.invalid(command)}_")
    }
  }

  override fun usage(prefix: String, lang: Lang): String {
    val translator = Babel.alias(lang)
    return translator.usage(prefix)
  }

  override fun description(lang: Lang): String = "Creates an alias, or alternate name, to a command for customization."
}
