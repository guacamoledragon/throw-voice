package tech.gdragon.commands.slash

import dev.minn.jda.ktx.interactions.commands.Command
import dev.minn.jda.ktx.messages.Embed
import net.dv8tion.jda.api.entities.MessageEmbed
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.core.eq
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.dao.Settings
import tech.gdragon.db.table.Tables
import tech.gdragon.discord.message.formatShortDateTime
import java.awt.Color
import net.dv8tion.jda.api.entities.Guild as DiscordGuild

object Info {
  val command = Command("info", "Display information about the bot for this specific server.")

  fun retrieveInfo(guild: DiscordGuild): MessageEmbed {
    return transaction {
      val settings = Tables.Settings
        .selectAll()
        .where { Tables.Settings.guild eq guild.idLong }
        .firstOrNull()
        ?.let(Settings::wrapRow)
      val dateJoined = Guild[guild.idLong].joinedOn
      val recordingCount = Tables.Recordings
        .selectAll()
        .where { Tables.Recordings.guild eq guild.idLong }
        .count()

      Embed {
        title = "Info"
        color = Color.decode("#596800").rgb
        description = "${guild.name} server information"
        field {
          name = ":hatching_chick: Joined"
          value = formatShortDateTime(dateJoined)
          inline = false
        }

        field {
          name = ":floppy_disk: Autosave"
          value = "`${settings?.autoSave}`"
        }
        field {
          name = ":speaking_head: Locale"
          value = "`${settings?.language}`"
        }

        field {
          name = ":headphones: Number of Recordings"
          value = "$recordingCount"
          inline = false
        }
        footer {
          name = "https://pawa.im"
        }
      }
    }
  }
}
