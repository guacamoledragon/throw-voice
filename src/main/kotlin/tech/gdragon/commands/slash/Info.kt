package tech.gdragon.commands.slash

import dev.minn.jda.ktx.Embed
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.commands.CommandHandler
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.table.Tables.Recordings
import tech.gdragon.i18n.Lang
import java.awt.Color
import net.dv8tion.jda.api.entities.Guild as DiscordGuild

class Info : CommandHandler() {
  companion object {
    val command = CommandData("info", "Display information about the bot for this specific server.")

    fun retrieveInfo(guild: DiscordGuild): MessageEmbed {
      return transaction {
        val prefix = Guild[guild.idLong].settings.prefix
        val dateJoined = Guild[guild.idLong].createdOn
        val recordingCount = Recordings.select { Recordings.guild eq guild.idLong }.count().toString()

        Embed {
          title = "Info"
          color = Color.decode("#596800").rgb
          description = "${guild.name} server information"
          field {
            name = ":hatching_chick: Joined"
            value = dateJoined.toString()
            inline = false
          }
          field {
            name = ":twisted_rightwards_arrows: Prefix"
            value = "`$prefix`"
            inline = false
          }
          field {
            name = ":headphones: Number of Recordings"
            value = recordingCount
            inline = false
          }
          footer {
            name = "https://pawa.im"
          }
        }
      }
    }
  }

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    val embed = retrieveInfo(event.guild)
    event.channel.sendMessageEmbeds(embed).queue()
  }

  override fun usage(prefix: String, lang: Lang): String = "${prefix}info"

  override fun description(lang: Lang): String = command.description
}
