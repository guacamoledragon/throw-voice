package tech.gdragon.commands.slash

import io.azam.ulidj.ULID
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import tech.gdragon.db.EmbeddedDatabase
import tech.gdragon.db.dao.Channel
import tech.gdragon.db.dao.Recording
import net.dv8tion.jda.api.entities.Guild as DiscordGuild
import tech.gdragon.db.dao.Guild as GuildDao

class InfoTest : FunSpec({

  val guildId = 100_004L

  lateinit var db: EmbeddedDatabase

  beforeSpec {
    db = EmbeddedDatabase("info-test", "mem", "DB_CLOSE_DELAY=-1")
    db.connect()
    db.migrate()
  }

  afterSpec {
    db.shutdown()
  }

  test("recording count excludes recordings that were never saved") {
    transaction {
      val guildDao = GuildDao.findOrCreate(guildId, "Test Guild Info")
      val channelDao = Channel.findOrCreate(200_004L, "test-voice", guildDao)
      Recording.new(ULID.random()) {
        channel = channelDao
        guild = guildDao
        url = "http://localhost/rec.mp3"
      }
      // A failed recording keeps the row that was created at start, with no url
      Recording.new(ULID.random()) {
        channel = channelDao
        guild = guildDao
      }
    }
    val guild = mockk<DiscordGuild> {
      every { idLong } returns guildId
      every { name } returns "Test Guild Info"
    }

    val embed = Info.retrieveInfo(guild)

    embed.fields.single { it.name == ":headphones: Number of Recordings" }.value shouldBe "1"
  }
})
