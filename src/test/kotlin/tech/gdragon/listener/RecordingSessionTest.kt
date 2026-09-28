package tech.gdragon.listener

import io.kotest.core.annotation.Isolate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.maps.shouldBeEmpty
import io.mockk.every
import io.mockk.mockk
import net.dv8tion.jda.api.audio.AudioReceiveHandler
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.internal.managers.AudioManagerImpl
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa

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

  test("leaving voice removes the session") {
    val recorder = mockk<AudioRecorder>(relaxed = true, moreInterfaces = arrayOf(AudioReceiveHandler::class)) {
      every { session } returns "S1"
    }
    val guild = guild()
    every { guild.audioManager } returns mockk<AudioManagerImpl>(relaxed = true) {
      every { receivingHandler } returns recorder as AudioReceiveHandler
    }
    val voiceChannel = mockk<AudioChannel>(relaxed = true) {
      every { this@mockk.guild } returns guild
    }
    pawa.startRecording("S1", guildId)

    BotUtils.leaveVoiceChannel(voiceChannel, mockk(relaxed = true), save = false)

    pawa.recordings.shouldBeEmpty()
  }

  test("the bot leaving voice removes the sessions of that guild only") {
    val event = mockk<GuildVoiceUpdateEvent>(relaxed = true) {
      every { guild } returns guild()
      every { member.user.isBot } returns true
      every { member.user.idLong } returns 42L
      every { member.user.jda.selfUser.idLong } returns 42L
    }
    pawa.startRecording("S1", guildId)
    pawa.startRecording("S2", otherGuildId)

    EventListener(pawa).onGuildVoiceLeave(event)

    pawa.recordings.shouldContainExactly(mapOf("S2" to otherGuildId))
  }

  test("the bot leaving the guild removes the sessions of that guild only") {
    val event = mockk<GuildLeaveEvent>(relaxed = true) {
      every { guild } returns guild()
    }
    pawa.startRecording("S1", guildId)
    pawa.startRecording("S2", otherGuildId)

    EventListener(pawa).onGuildLeave(event)

    pawa.recordings.shouldContainExactly(mapOf("S2" to otherGuildId))
  }
})
