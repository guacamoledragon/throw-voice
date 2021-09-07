package tech.gdragon.commands.slash

import dev.minn.jda.ktx.Embed
import dev.minn.jda.ktx.onCommand
import net.dv8tion.jda.api.interactions.commands.build.CommandData
import net.dv8tion.jda.api.sharding.ShardManager
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.java.KoinJavaComponent.getKoin
import tech.gdragon.db.dao.Guild
import tech.gdragon.db.table.Tables.Recordings
import tech.gdragon.metrics.EventTracer
import java.awt.Color

object Info {
  val command = CommandData("info", "Display information about the bot for this specific server.")
}

val tracer: EventTracer = getKoin().get()

fun registerSlashCommands(shardManager: ShardManager) {
  shardManager
    .onCommand("info") { event ->
      tracer.sendEvent(mapOf("command" to "INFO"))
      if (event.isFromGuild) {
        transaction {
          event.guild?.let {
            transaction {
              val prefix = Guild[it.idLong].settings.prefix
              val dateJoined = Guild[it.idLong].createdOn
              val recordingCount = Recordings.select { Recordings.guild eq it.idLong }.count().toString()

              val embed = Embed {
                title = "Info"
                color = Color.decode("#596800").rgb
                description = "${it.name} server information"
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

              event
                .replyEmbeds(embed)
                .queue()
            }
          }
        }
      } else {
        event
          .reply(":no_entry: _Must be in a server to run this command!_")
          .queue()
      }
    }
}
