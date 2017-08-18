package tech.gdragon

import net.dv8tion.jda.core.entities.VoiceChannel
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.db.dao.Guild

object BotUtils {
  /**
   * Find biggest voice chanel that surpasses the Guild's autoJoin minimum
   */
  @JvmStatic
  fun biggestChannel(voiceChannels: List<VoiceChannel>): VoiceChannel {
    return transaction {
      voiceChannels.maxBy { voiceChannel ->
        val guild = Guild.findById(voiceChannel.guild.idLong)
        val settings = guild?.settings
        val channel = settings?.channels?.findLast { it.id.value == voiceChannel.idLong }

        val channelSize = voiceChannelSize(voiceChannel)

        channel?.autoJoin?.let {
          if (it <= channelSize)
            channelSize
          else
            0
        } ?: 0
      }
    }!!
/*    return voiceChannels
      .filter { voiceChannel ->
        transaction {
          Channel
            .find {
              (Tables.Channels.id eq voiceChannel.idLong).and(Tables.Guilds.id eq voiceChannel.guild.idLong)
            }
            .all { channel ->
              channel.autoJoin?.let { it >= voiceChannelSize(voiceChannel) } ?: false
            }
        }
      }
      .maxBy { voiceChannelSize(it) }!!*/
  }

  /**
   * Returns the effective size of the voice channel, excluding bots.
   */
  @JvmStatic
  fun voiceChannelSize(voiceChannel: VoiceChannel?): Int {
    return voiceChannel?.members?.count { !it.user.isBot } ?: 0
  }

  @JvmStatic
  fun testQuery(): Unit {
    transaction {
      /*val things = Guild.find { Tables.Settings.id eq 1L and (Tables.Guilds.id eq 1L) }
      things.forEach { println(it.name) }*/
//      val guild = Guild.find { (Tables.Guilds.id eq 1L) and (Tables.SettingsChannels.settings?.eq(1L)) }
//      println(guild.first().name)
    }
  }
}
