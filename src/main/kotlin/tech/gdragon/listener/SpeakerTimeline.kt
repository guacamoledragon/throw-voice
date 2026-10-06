package tech.gdragon.listener

import clojure.java.api.Clojure
import clojure.lang.Keyword

/**
 * Who speaks when, counted in the 20 ms frames that went into the recording.
 *
 * Only the audio processing thread calls [add] and [trim], so there is no locking.
 */
class SpeakerTimeline(private val gapFrames: Long = 15) {
  companion object {
    const val FRAME_MS = 20L
    private val prStr = Clojure.`var`("clojure.core", "pr-str")
    private fun kw(name: String) = Keyword.intern(name)
  }

  private val names = LinkedHashMap<Long, String>()
  private val open = HashMap<Long, LongArray>()
  private val segments = mutableListOf<Triple<Long, Long, Long>>()
  private var frame = 0L
  private var trimmed = 0L

  var speechFrames = 0L
    private set
  var overlapFrames = 0L
    private set
  val speakerCount: Int get() = names.size

  /** Add the next frame. [speakers] maps a Discord user ID to a display name. */
  fun add(speakers: Map<Long, String>) {
    if (speakers.isNotEmpty()) speechFrames++
    if (speakers.size > 1) overlapFrames++
    for ((id, name) in speakers) {
      names.putIfAbsent(id, name)
      open.getOrPut(id) { longArrayOf(frame, frame) }[1] = frame
    }
    close { lastSeen -> frame - lastSeen > gapFrames }
    frame++
  }

  /** The recorder removed the oldest [frames] from the recording. */
  fun trim(frames: Long) {
    trimmed += frames
  }

  private fun close(done: (Long) -> Boolean) {
    val iterator = open.iterator()
    while (iterator.hasNext()) {
      val (id, span) = iterator.next()
      if (done(span[1])) {
        segments += Triple(id, span[0], span[1] + 1)
        iterator.remove()
      }
    }
  }

  /**
   * Close the open segments and print the timeline as EDN. Times are milliseconds from the start of the recording.
   * A segment is `[speaker-index start end]`, and the index points into `:speakers`.
   */
  fun toEdn(session: String): String {
    close { true }
    val index = names.keys.withIndex().associate { (i, id) -> id to i }
    val edn = linkedMapOf(
      kw("version") to 1,
      kw("session") to session,
      kw("speakers") to names.map { (id, name) -> linkedMapOf(kw("id") to id.toString(), kw("name") to name) },
      kw("segments") to segments
        .filter { it.third > trimmed }
        .sortedBy { it.second }
        .map { (id, start, end) ->
          listOf(index[id], (start - trimmed).coerceAtLeast(0) * FRAME_MS, (end - trimmed) * FRAME_MS)
        }
    )
    return prStr.invoke(edn) as String
  }
}
