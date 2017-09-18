package tech.gdragon

import net.dv8tion.jda.core.entities.VoiceChannel
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.db.dao.Guild
import net.dv8tion.jda.core.entities.Guild as DiscordGuild

object BotUtils {
  /**
   * Find biggest voice chanel that surpasses the Guild's autoJoin minimum
   */
  @JvmStatic
  fun biggestChannel(guild: DiscordGuild): VoiceChannel? {
    val voiceChannels = guild.voiceChannels

    return transaction {
      val settings = Guild.findById(guild.idLong)?.settings

      voiceChannels
        .filter { voiceChannel ->
          val channel = settings?.channels?.find { it.discordId == voiceChannel.idLong }
          val channelSize = voiceChannelSize(voiceChannel)
          channel?.autoJoin?.let { it <= channelSize } ?: false
        }
        .maxBy(BotUtils::voiceChannelSize)
    }
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
