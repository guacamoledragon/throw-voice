package tech.gdragon.commands.slash

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import tech.gdragon.commands.CommandHandler
import tech.gdragon.i18n.Lang

class Bootstrap : CommandHandler() {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    val willCreate = args.first().toBoolean()
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
    println("updating slash commands")
  }

  override fun usage(prefix: String, lang: Lang): String = "${prefix}bootstrap"

  override fun description(lang: Lang): String = "Installs slash commands"
}
