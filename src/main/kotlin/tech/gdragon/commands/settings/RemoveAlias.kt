package tech.gdragon.commands.settings

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.db.dao.Guild

class RemoveAlias : CommandHandler {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.size == 1) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    transaction {
      val guild = Guild.findById(event.guild.idLong)
      val defaultChannel = BotUtils.defaultTextChannel(event.guild) ?: event.channel
      val alias = args.first().toLowerCase()

      guild?.settings?.let { settings ->
        settings.aliases.find { it.alias == alias }?.delete()
        BotUtils.sendMessage(defaultChannel, "Alias '$alias' has been removed.")
      }
    }
  }

  override fun usage(prefix: String): String = "${prefix}removeAlias [alias name]"

  override fun description(): String = "Removes an alias from a command."
}
