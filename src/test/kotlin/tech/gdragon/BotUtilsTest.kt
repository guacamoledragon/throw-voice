package tech.gdragon

import io.azam.ulidj.ULID
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class BotUtilsTest : FunSpec({

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
