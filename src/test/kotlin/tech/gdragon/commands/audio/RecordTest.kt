package tech.gdragon.commands.audio

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.api.pawa.PawaConfig
import tech.gdragon.db.EmbeddedDatabase
import net.dv8tion.jda.api.entities.Guild as DiscordGuild
import tech.gdragon.db.dao.Guild as GuildDao

class RecordTest : FunSpec({

  val guildId = 100_005L

  lateinit var db: EmbeddedDatabase

  beforeSpec {
    db = EmbeddedDatabase("record-test", "mem", "DB_CLOSE_DELAY=-1")
    db.connect()
    db.migrate()
    transaction { GuildDao.findOrCreate(guildId, "Test Guild Record") }
  }

  afterSpec {
    db.shutdown()
  }

  test("record replies that the channel is full when JDA refuses to join it") {
    val guild = mockk<DiscordGuild>(relaxed = true) {
      every { idLong } returns guildId
      every { name } returns "Test Guild Record"
      every { audioManager.isConnected } returns false
      every { selfMember.hasPermission(any<GuildChannel>(), *anyVararg()) } returns true
    }
    val voiceChannel = mockk<AudioChannel>(relaxed = true) {
      every { this@mockk.guild } returns guild
      every { id } returns "200005"
      every { name } returns "test-voice"
    }
    // JDA throws this from openAudioConnection when the user limit is reached
    every { guild.audioManager.openAudioConnection(voiceChannel) } throws
      InsufficientPermissionException(voiceChannel, Permission.VOICE_MOVE_OTHERS, "Unable to connect to AudioChannel due to userlimit!")
    val messageChannel = mockk<MessageChannel>(relaxed = true) {
      every { canTalk() } returns true
      every { jda.getGuildChannelById(any<Long>())!!.guild } returns guild
    }

    val reply = Record.handler(Pawa(db, PawaConfig { isStandalone = false }), guild, voiceChannel, messageChannel)

    reply.content shouldContain "is full"
  }
})
