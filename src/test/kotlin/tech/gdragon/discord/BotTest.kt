package tech.gdragon.discord

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.EventListener
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BotTest : FunSpec({
  test("the bot asks for embed links, and the site invite link asks for Bot.PERMISSIONS") {
    Bot.PERMISSIONS shouldContain Permission.MESSAGE_EMBED_LINKS
    val permissions = "permissions=${Permission.getRaw(Bot.PERMISSIONS)}\""
    File("src/site/quickstart.md").readText() shouldContain permissions
    File("src/site/README.md").readText() shouldContain permissions
  }

  test("an event is handled while more listeners block than the machine has processors") {
    val manager = Bot.eventManager()
    val blocking = mockk<GenericEvent>()
    val next = mockk<GenericEvent>()
    val release = CountDownLatch(1)
    val handled = CountDownLatch(1)
    manager.register(EventListener { if (it === blocking) release.await() else handled.countDown() })

    repeat(Runtime.getRuntime().availableProcessors() + 1) { manager.handle(blocking) }
    manager.handle(next)

    try {
      handled.await(2, TimeUnit.SECONDS) shouldBe true
    } finally {
      release.countDown()
    }
  }
})
