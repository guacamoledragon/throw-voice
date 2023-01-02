package tech.gdragon.commands.slash

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.commands.audio.Record
import tech.gdragon.commands.audio.Save
import tech.gdragon.commands.audio.Stop
import tech.gdragon.commands.settings.*
import tech.gdragon.discord.Bot
import tech.gdragon.i18n.Lang

class Slash : CommandHandler() {
  override fun action(args: Array<String>, event: MessageReceivedEvent, pawa: Pawa) {
    require(standalone || BotUtils.trigoman == event.author.idLong) {
      throw InvalidCommand({ "Command can only be used by server admins." }, "Unauthorized use.")
    }

    val action = args.firstOrNull()
    val sendMessage = { msg: () -> String ->
      val channel = event.channel
      BotUtils.sendMessage(channel, msg())
    }

    event.jda.let {
      when (action) {
        "invite" -> sendMessage {
          "Invite URL: " + it.setRequiredScopes("applications.commands").getInviteUrl(Bot.PERMISSIONS)
        }

        "list" -> it.retrieveCommands().queue { commands ->
          sendMessage {
            commands.joinToString(prefix = "Available Commands: ") { command -> command.name }
              .ifEmpty { "No commands!" }
          }
        }

        "add" -> it.updateCommands().
        also { commands ->
          commands.addCommands(
            Alias.command,
            AutoStop.command,
            AutoSave.command,
            Ignore.command,
            Info.command,
            Language.command,
            Record.command,
            Stop.command,
            Save.command,
            Volume.command
          )
        }.queue { commands ->
          sendMessage {
            commands.joinToString(prefix = "Adding: ") { command -> command.name }.ifEmpty { "No commands!" }
          }
        }

        "remove" -> it.retrieveCommands().queue { commands ->
          commands.forEach { command ->
            it.deleteCommandById(command.idLong).queue {
              sendMessage { "Removed ${command.name}!" }
            }
          }
        }

        else -> sendMessage { "Invalid command!\nChoose one of: `invite, list, add, or remove`." }
      }
    }
  }

  override fun usage(prefix: String, lang: Lang): String = "${prefix}bootstrap"

  override fun description(lang: Lang): String = "Installs slash commands"
}
