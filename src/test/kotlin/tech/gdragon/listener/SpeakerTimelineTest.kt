package tech.gdragon.listener

import clojure.java.api.Clojure
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SpeakerTimelineTest : FunSpec({
  val alice = 1498928156672000150L to "Alice \"A\""
  val bob = 2L to "Bob"

  fun SpeakerTimeline.frames(count: Int, vararg speakers: Pair<Long, String>) =
    repeat(count) { add(mapOf(*speakers)) }

  test("joins short pauses, splits long ones, and counts overlap") {
    val timeline = SpeakerTimeline(gapFrames = 5)
    timeline.frames(10, alice)       // 0..200 ms
    timeline.frames(3)               // short pause, same segment
    timeline.frames(5, alice, bob)   // 260..360 ms, overlap
    timeline.frames(20)              // long pause, both segments end
    timeline.frames(4, bob)          // 760..840 ms

    timeline.speechFrames shouldBe 19
    timeline.overlapFrames shouldBe 5
    timeline.speakerCount shouldBe 2

    val edn = Clojure.`var`("clojure.edn", "read-string").invoke(timeline.toEdn("S1"))
    edn.toString() shouldBe
      """{:version 1, :session "S1", """ +
      """:speakers [{:id "1498928156672000150", :name "Alice \"A\""} {:id "2", :name "Bob"}], """ +
      """:segments [[0 0 360] [1 260 360] [1 760 840]]}"""
  }

  test("trim shifts the times and drops removed segments") {
    val timeline = SpeakerTimeline(gapFrames = 0)
    timeline.frames(10, bob)
    timeline.frames(10)
    timeline.frames(10, bob)
    timeline.trim(15)

    timeline.toEdn("S2") shouldBe
      """{:version 1, :session "S2", :speakers [{:id "2", :name "Bob"}], :segments [[0 100 300]]}"""
  }
})
