package tech.gdragon.commands.slash

import dev.minn.jda.ktx.interactions.command
import dev.minn.jda.ktx.interactions.updateCommands
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.discord.Bot
import tech.gdragon.i18n.Lang

class Slash : CommandHandler() {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(standalone || BotUtils.trigoman == event.author.idLong) {
      throw InvalidCommand({ "Command can only be used by server admins." }, "Unauthorized use.")
    }

    val action = args.first()
    val channel = event.channel

    event.jda.let {
      when (action) {
        "invite" -> BotUtils.sendMessage(
          channel,
          it.setRequiredScopes("applications.commands").getInviteUrl(Bot.PERMISSIONS)
        )
        "list" -> it.retrieveCommands().queue { commands ->
          BotUtils.sendMessage(channel, commands.joinToString { command -> command.name }.ifEmpty { "No commands!" })
        }
        "add" -> it.updateCommands {
          command(Info.command.name, Info.command.description)
        }.queue { commands ->
          BotUtils.sendMessage(channel, commands.joinToString { command -> command.name }.ifEmpty { "No commands!" })
        }
        "remove" -> it.retrieveCommands().queue { commands ->
          commands.forEach { command ->
            it.deleteCommandById(command.idLong).queue {
              BotUtils.sendMessage(channel, "Removed ${command.name}!")
            }
          }
        }
        else -> BotUtils.sendMessage(channel, "Invalid command!")
      }
    }

/*    val willCreate = args.first().toBoolean()
    if (willCreate) {
      event.jda
        .upsertCommand(Info.command)
    }
    event.jda
      .retrieveCommands()
      .complete()
      .forEach { command ->
        event.jda.deleteCommandById(command.id).queue()
      }
    println("updating slash commands")*/
  }

  override fun usage(prefix: String, lang: Lang): String = "${prefix}bootstrap"

  override fun description(lang: Lang): String = "Installs slash commands"
}
