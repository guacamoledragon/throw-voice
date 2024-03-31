package tech.gdragon.commands.debug

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.CommandHandler
import tech.gdragon.db.asyncTransaction
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.now
import tech.gdragon.i18n.Lang

class Async : CommandHandler() {
  override fun action(args: Array<String>, event: MessageReceivedEvent, pawa: Pawa) {
    for (i in 1..1000) {
      asyncTransaction {
        val guild = Guild[333055724198559745L]
        guild.lastActiveOn = now()
        print("$i,")
      }
    }
  }

  override fun usage(prefix: String, lang: Lang): String {
    TODO("Not yet implemented")
  }

  override fun description(lang: Lang): String {
    TODO("Not yet implemented")
  }
}
