package tech.gdragon.commands.settings

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.BotUtils
import tech.gdragon.commands.Command
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.dao.User

fun configureAlerts(userId: String, guildId: Long, enable: Boolean) {
  transaction {
    val settings = Guild.findById(guildId)?.settings!!
    val user = User.findOrCreate(userId, settings)

    if (enable) {
      user.delete()
    }
  }
}

class Alerts : Command {

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    val botId = event.jda.selfUser.id
    val message = ":no_entry_sign: _<@$botId> will no longer send alerts, this command will be removed in the next major release._"
    BotUtils.sendMessage(event.channel, message)
  }

  override fun usage(prefix: String): String = "${prefix}alerts [on | off]"

  override fun description(): String =
    "Turns on/off direct message alerts when you are being recorded in a voice channel (on by default)"
}
