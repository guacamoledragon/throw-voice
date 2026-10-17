package tech.gdragon

import io.azam.ulidj.ULID
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import java.time.Duration
import java.time.OffsetDateTime

class BotUtilsTest : FunSpec({

  test("interactionContext gives the age of the interaction in milliseconds") {
    val event = mockk<GenericCommandInteractionEvent>(relaxed = true)
    val created = OffsetDateTime.now().minusSeconds(4)
    every { event.timeCreated } returns created

    val age = BotUtils.interactionContext(event).getValue("interaction.age.ms")!!.toLong()

    age shouldBeInRange 4000L..Duration.between(created, OffsetDateTime.now()).toMillis()
  }

  context("findSessionID") {
    test("it should return a list with one ULID when message has one ULID") {
      val message = ULID.random()

      val result = BotUtils.findSessionID(message)

      result.shouldHaveSize(1)
      result.first().shouldBe(message)
    }

    test("it should return an empty list when message has no valid ULIDs") {
      val message = ULID.random().drop(5)

      val result = BotUtils.findSessionID(message)

      result.shouldHaveSize(0)
    }

    test("it should a list with many ULIDs when message contains many ULIDs") {
      val id1 = ULID.random()
      val id2 = ULID.random()
      val id3 = ULID.random()
      val id4 = ULID.random()
      val id5 = ULID.random().drop(5)
      val id6 = ULID.random()
      val message = """
        Could you help me recover these IDs $id1,$id2 $id3
        oh and this other one too $id4
        also this other one$id5 that my friend forgot to send
        is this expired????$id6$id6
      """.trimIndent()

      val result = BotUtils.findSessionID(message)

      result.shouldHaveSize(5)
      result.shouldContainExactly(id1, id2, id3, id4, id6)
    }
  }
})
