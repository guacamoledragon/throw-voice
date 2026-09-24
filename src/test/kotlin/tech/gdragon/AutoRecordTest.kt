package tech.gdragon

import io.kotest.core.annotation.Isolate
import io.kotest.core.spec.style.FunSpec
import io.mockk.*
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.api.pawa.PawaConfig
import tech.gdragon.db.EmbeddedDatabase
import tech.gdragon.db.dao.Channel
import net.dv8tion.jda.api.entities.Guild as DiscordGuild
import tech.gdragon.db.dao.Guild as GuildDao

@Isolate
class AutoRecordTest : FunSpec({

  val guildId = 100_003L
  val voiceChannelId = 200_003L

  lateinit var db: EmbeddedDatabase

  beforeSpec {
    db = EmbeddedDatabase("autorecord-test", "mem", "DB_CLOSE_DELAY=-1")
    db.connect()
    db.migrate()

    transaction {
      GuildDao.findOrCreate(guildId, "Test Guild AutoRecord")
      Channel.findOrCreate(voiceChannelId, "test-voice", guildId).autoRecord = 1
    }

    mockkObject(BotUtils)
  }

  afterSpec {
    unmockkObject(BotUtils)
    db.shutdown()
  }

  test("autorecord does not send a message when the bot is already in a channel") {
    val guild = mockk<DiscordGuild>(relaxed = true) {
      every { idLong } returns guildId
      every { name } returns "Test Guild AutoRecord"
      every { audioManager.isConnected } returns true
    }
    val channel = mockk<AudioChannelUnion>(relaxed = true) {
      every { idLong } returns voiceChannelId
      every { name } returns "test-voice"
      every { members } returns listOf(mockk(relaxed = true))
    }

    BotUtils.autoRecord(Pawa(db, PawaConfig { isStandalone = false }), guild, channel)

    verify(exactly = 0) { BotUtils.sendMessage(any(), any<String>()) }
    verify(exactly = 0) { BotUtils.recordVoiceChannel(any(), any()) }
  }
})
