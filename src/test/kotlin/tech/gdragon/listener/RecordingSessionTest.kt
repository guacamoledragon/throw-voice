package tech.gdragon.listener

import io.kotest.core.annotation.Isolate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import net.dv8tion.jda.api.audio.AudioReceiveHandler
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.internal.managers.AudioManagerImpl
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.audio.Record
import tech.gdragon.db.Database
import tech.gdragon.i18n.Babel
import tech.gdragon.i18n.Lang
import tech.gdragon.i18n.Record as RecordTranslator

@Isolate
class RecordingSessionTest : FunSpec({
  val guildId = 1L
  val otherGuildId = 2L

  lateinit var pawa: Pawa

  beforeTest {
    pawa = Pawa(mockk())
    startKoin { modules(module { single { pawa } }) }
  }

  afterTest { stopKoin() }

  fun guild(): Guild = mockk(relaxed = true) {
    every { idLong } returns guildId
  }

  fun recorder(session: String) =
    mockk<AudioRecorder>(relaxed = true, moreInterfaces = arrayOf(AudioReceiveHandler::class)) {
      every { this@mockk.session } returns session
    }

  test("leaving voice removes the session") {
    val recorder = recorder("S1")
    val guild = guild()
    every { guild.audioManager } returns mockk<AudioManagerImpl>(relaxed = true) {
      every { receivingHandler } returns recorder as AudioReceiveHandler
    }
    val voiceChannel = mockk<AudioChannel>(relaxed = true) {
      every { this@mockk.guild } returns guild
    }
    pawa.startRecording(recorder, guildId)

    BotUtils.leaveVoiceChannel(voiceChannel, mockk(relaxed = true), save = false)

    pawa.recordings.shouldBeEmpty()
  }

  test("the bot leaving voice closes the recorder of that guild only") {
    val recorder = recorder("S1")
    val other = recorder("S2")
    pawa = spyk(pawa) {
      every { autoSave(guildId) } returns false
    }
    val guild = guild()
    every { guild.audioManager } returns mockk<AudioManagerImpl>(relaxed = true) {
      every { receivingHandler } returns recorder as AudioReceiveHandler
    }
    val event = mockk<GuildVoiceUpdateEvent>(relaxed = true) {
      every { this@mockk.guild } returns guild
      every { channelLeft!!.guild } returns guild
      every { member.user.isBot } returns true
      every { member.user.idLong } returns 42L
      every { member.user.jda.selfUser.idLong } returns 42L
    }
    pawa.startRecording(recorder, guildId)
    pawa.startRecording(other, otherGuildId)

    EventListener(pawa).onGuildVoiceLeave(event)

    verify { recorder.disconnect(false, null, null) }
    verify(exactly = 0) { recorder.saveRecording(any(), any()) }
    verify(exactly = 0) { other.disconnect(any(), any(), any()) }
    pawa.recordings.shouldContainExactly(mapOf("S2" to otherGuildId))
  }

  test("record does not start a second recording while the first one connects") {
    pawa = spyk(pawa) {
      every { language(any()) } returns Lang.EN
    }
    val audioManager = mockk<AudioManagerImpl>(relaxed = true) {
      every { isConnected } returns false
      every { connectedChannel } returns null
    }
    val guild = guild()
    every { guild.audioManager } returns audioManager
    val voiceChannel = mockk<AudioChannel>(relaxed = true) {
      every { this@mockk.guild } returns guild
      every { id } returns "9"
    }
    pawa.startRecording(recorder("S1"), guildId)

    val message = Record.handler(pawa, guild, voiceChannel, mockk(relaxed = true))

    message.content shouldBe ":no_entry_sign: _${Babel.commandTranslator<RecordTranslator>(Lang.EN).alreadyInChannel("9")}_"
    verify(exactly = 0) { audioManager.openAudioConnection(any()) }
    pawa.recordings.shouldContainExactly(mapOf("S1" to guildId))
  }

  test("the bot leaving the guild removes the sessions of that guild only") {
    val event = mockk<GuildLeaveEvent>(relaxed = true) {
      every { guild } returns guild()
    }
    pawa.startRecording(recorder("S1"), guildId)
    pawa.startRecording(recorder("S2"), otherGuildId)

    EventListener(pawa).onGuildLeave(event)

    pawa.recordings.shouldContainExactly(mapOf("S2" to otherGuildId))
  }

  test("maintenance mode stops a new recording and tells the user") {
    pawa = spyk(pawa) {
      every { language(any()) } returns Lang.EN
    }
    pawa.maintenance = true
    val audioManager = mockk<AudioManagerImpl>(relaxed = true)
    val guild = guild()
    every { guild.audioManager } returns audioManager
    val voiceChannel = mockk<AudioChannel>(relaxed = true) {
      every { this@mockk.guild } returns guild
    }

    val message = Record.handler(pawa, guild, voiceChannel, mockk(relaxed = true))

    message.content shouldBe ":tools: _${Babel.commandTranslator<RecordTranslator>(Lang.EN).maintenance}_"
    verify(exactly = 0) { audioManager.openAudioConnection(any()) }
    pawa.recordings.shouldBeEmpty()
  }

  test("BOT_MAINTENANCE sets the start value of maintenance mode") {
    fun maintenance(value: String?) = koinApplication {
      val required = mapOf("BOT_STANDALONE" to "false", "BOT_RECOVER_ENABLED" to "true")
      properties(required + listOfNotNull(value?.let { "BOT_MAINTENANCE" to it }))
      modules(module { single<Database> { mockk() } }, Pawa.module())
    }.koin.get<Pawa>().maintenance

    maintenance("true") shouldBe true
    maintenance("") shouldBe false
    maintenance(null) shouldBe false
  }

  test("silenceUser silences the user in the active recorder of the guild") {
    val recorder = recorder("S1")
    val other = recorder("S2")
    pawa.startRecording(recorder, guildId)
    pawa.startRecording(other, otherGuildId)

    pawa.silenceUser(guildId, 7L) shouldBe true

    verify { recorder.silenceUser(7L) }
    verify(exactly = 0) { other.silenceUser(any()) }
  }

  test("silenceUser returns false when the guild has no recording") {
    val recorder = recorder("S1")
    pawa.startRecording(recorder, guildId)
    pawa.stopRecording("S1")

    pawa.silenceUser(guildId, 7L) shouldBe false

    verify(exactly = 0) { recorder.silenceUser(any()) }
  }
})
