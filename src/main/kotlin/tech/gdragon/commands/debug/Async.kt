package tech.gdragon.commands.debug

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.joda.time.DateTime
import tech.gdragon.commands.CommandHandler
import tech.gdragon.db.asyncTransaction
import tech.gdragon.db.dao.Guild

class Async : CommandHandler() {
  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    for (i in 1..1000) {
      asyncTransaction {
        val guild = Guild[333055724198559745L]
        guild.lastActiveOn = DateTime.now()
        print("$i,")
      }
    }
  }

  override fun usage(prefix: String): String {
    TODO("Not yet implemented")
  }

  override fun description(): String {
    TODO("Not yet implemented")
  }
}
