package tech.gdragon.commands.settings

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.Command
import tech.gdragon.db.dao.Guild

class RemoveAlias : Command {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    transaction {
      val guild = Guild.findById(event.guild.idLong)
      val prefix = guild?.settings?.prefix ?: "!"

      require(args.size == 1) {
        BotUtils.sendMessage(event.channel, usage(prefix))
      }

      val alias = args.first().toLowerCase()

      guild?.settings?.let {
        it.aliases.find { it.alias == alias }?.delete()
        BotUtils.sendMessage(event.channel, "Alias '$alias' has been removed.")
      }
    }
  }

  override fun usage(prefix: String): String = "${prefix}removeAlias [alias name]"

  override fun description(): String = "Removes an alias from a command."
}
