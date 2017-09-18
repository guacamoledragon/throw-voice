package tech.gdragon

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.entities.VoiceChannel
import org.jetbrains.exposed.sql.transactions.transaction
import tech.gdragon.db.dao.Guild
import java.awt.Color
import java.time.OffsetDateTime
import net.dv8tion.jda.core.entities.Guild as DiscordGuild

object BotUtils {
  /**
   * Send a DM to anyone in the voiceChannel unless they are in the blacklist
   */
  @JvmStatic
  fun alert(voiceChannel: VoiceChannel?) {
    transaction {
      val guild = Guild.findById(voiceChannel?.guild?.idLong ?: 0)
      val blackList = guild?.settings?.alertBlacklist
      val message = EmbedBuilder()
        .setAuthor("pawabot", "https://github.com/guacamoledragon/throw-voice", voiceChannel?.jda?.selfUser?.avatarUrl)
        .setColor(Color.RED)
        .setTitle("Your audio is now being recorded in ${voiceChannel?.name} on ${voiceChannel?.guild?.name}.")
        .setDescription("Disable this alert with `${guild?.settings?.prefix}alerts off`")
        .setThumbnail("http://www.freeiconspng.com/uploads/alert-icon-png-red-alert-round-icon-clip-art-3.png")
        .setTimestamp(OffsetDateTime.now())
        .build()

      voiceChannel?.members
        ?.map { it.user }
        ?.filter { user -> !user.isBot && blackList?.find { it.discordId == user.idLong } == null}
        ?.forEach { user ->
          user.openPrivateChannel().queue { channel ->
              channel.sendMessage(message).queue()
          }
        }
    }
  }

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
