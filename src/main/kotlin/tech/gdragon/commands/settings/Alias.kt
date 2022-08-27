package tech.gdragon.commands.settings

import dev.minn.jda.ktx.interactions.Command
import dev.minn.jda.ktx.interactions.choice
import dev.minn.jda.ktx.interactions.option
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.asyncTransaction
import tech.gdragon.db.dao.Alias
import tech.gdragon.db.dao.Guild
import tech.gdragon.discord.Command
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang

class Alias : CommandHandler() {
  companion object {
    val command = Command("alias", "Creates an alias, or alternate name, to a command for customization.") {
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

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.size == 2) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
    val command = args.first().uppercase()
    val translator = transaction { Guild[event.guild.idLong].settings.language.let(Babel::alias) }

    // Checks that command to alias exists
    if (Command.ALIAS.name == command || Command.values().none { it.name == command }) {
      BotUtils.sendMessage(defaultChannel, ":no_entry_sign: _${translator.invalid(command.lowercase())}_")
    } else {
      val aliases = transaction { Guild.findById(event.guild.idLong)?.settings?.aliases?.map { it.alias } }
      val alias = args[1]

      val message =
        when {
          // Checks that alias doesn't already exist
          aliases?.any { it == alias } == true -> ":no_entry_sign: _${translator.exists(alias)}_"
          // Checks that alias isn't a command
          command == alias.uppercase() -> ":no_entry_sign: _${translator.command(alias)}_"
          else -> {
            asyncTransaction {
              Guild.findById(event.guild.idLong)?.settings?.let {
                Alias.new {
                  name = command
                  this.alias = alias
                  settings = it
                }
              }
            }
            ":dancers: _${translator.new(alias, command.lowercase())}_"
          }
        }

      BotUtils.sendMessage(defaultChannel, message)
    }
  }

  override fun usage(prefix: String, lang: Lang): String {
    val translator = Babel.alias(lang)
    return translator.usage(prefix)
  }

  override fun description(lang: Lang): String = "Creates an alias, or alternate name, to a command for customization."
}
