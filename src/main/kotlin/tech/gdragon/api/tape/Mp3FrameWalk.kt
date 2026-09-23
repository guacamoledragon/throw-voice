package tech.gdragon.api.tape

import java.io.File
import java.io.RandomAccessFile

/**
 * The result of one pass over the MPEG frame headers of an mp3 file.
 *
 * See work item #96: a recording can end with a frame whose header declares more bytes than
 * the file holds. ffmpeg copies that frame and counts it in the Xing header, which makes the
 * declared sample count exceed the data. Readers that trust the Xing count — Apple's
 * `AVAudioFile`, and therefore `SpeechAnalyzer` — fail on the last packet.
 */
data class Mp3Walk(
  /** Offset of the first frame header, after any ID3v2 tag. */
  val audioStart: Long,
  /** Complete frames only. */
  val frameCount: Int,
  /** End offset of the last complete frame. */
  val lastFrameEnd: Long,
  /** Bytes the last frame declares but the file does not hold. 0 when the tail is clean. */
  val shortfall: Long,
  /** Times the walk had to search for the next sync word. */
  val resyncCount: Int
)

/**
 * MDC fields for one walk, for `withLoggingContext`.
 *
 * `log4j2-prod.xml` writes through `EcsLayout.json`, which flattens the MDC into the JSON
 * root and stringifies every value. Each key below therefore becomes a string column in the
 * Honeycomb `pawa` dataset, next to `audio.frames.dropped` and `audio.queue.depth`. These
 * names are a query contract — renaming one breaks the boards that read it.
 */
fun Mp3Walk.loggingFields(stage: String, fileBytes: Long): Map<String, String> = mapOf(
  "audio.mp3.stage" to stage,
  "audio.mp3.tail.shortfall" to shortfall.toString(),
  "audio.mp3.frame.count" to frameCount.toString(),
  "audio.mp3.resync.count" to resyncCount.toString(),
  "audio.mp3.file.bytes" to fileBytes.toString()
)

/** Bitrates in kbps by index, MPEG-1 Layer III. Index 0 (free) and 15 (bad) are rejected. */
private val MPEG1_BITRATES = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)

/** Bitrates in kbps by index, MPEG-2 and MPEG-2.5 Layer III. */
private val MPEG2_BITRATES = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0)

/** Sample rates by version bits (0 = MPEG-2.5, 1 = reserved, 2 = MPEG-2, 3 = MPEG-1) then index. */
private val SAMPLE_RATES = arrayOf(
  intArrayOf(11025, 12000, 8000, 0),
  intArrayOf(0, 0, 0, 0),
  intArrayOf(22050, 24000, 16000, 0),
  intArrayOf(44100, 48000, 32000, 0)
)

/**
 * Walks the frame headers from the first frame to the end of the file.
 *
 * Returns null when the file holds no valid Layer III frame header. One forward pass, so a
 * multi-megabyte recording costs a few milliseconds.
 */
fun walkMp3Frames(mp3: File): Mp3Walk? {
  val length = mp3.length()
  if (length <= 0L) return null

  RandomAccessFile(mp3, "r").use { raf ->
    val reader = BlockReader(raf, length)
    val audioStart = syncFrom(reader, id3v2End(reader), length) ?: return null

    var pos = audioStart
    var frameCount = 0
    var resyncCount = 0
    var lastFrameEnd = audioStart
    var shortfall = 0L

    while (pos < length) {
      val frameLength = frameLengthAt(reader, pos)

      if (frameLength == null) {
        // Either a gap in the stream or trailing bytes that are not a frame.
        val next = syncFrom(reader, pos + 1, length) ?: break
        resyncCount++
        pos = next
        continue
      }

      if (pos + frameLength > length) {
        shortfall = pos + frameLength - length
        break
      }

      frameCount++
      lastFrameEnd = pos + frameLength
      pos += frameLength
    }

    return Mp3Walk(audioStart, frameCount, lastFrameEnd, shortfall, resyncCount)
  }
}

/**
 * Removes everything after the last complete frame, so the file ends on a frame boundary.
 * Returns the number of bytes removed, or 0 when the file needs no repair.
 *
 * An ID3v1 tag or an ID3v2 footer is a legitimate trailer, not damage, and stays.
 */
fun trimIncompleteTrailingFrame(mp3: File): Long {
  val walk = walkMp3Frames(mp3) ?: return 0L
  val trailing = mp3.length() - walk.lastFrameEnd
  if (trailing <= 0L) return 0L
  if (hasKnownTrailer(mp3, walk.lastFrameEnd)) return 0L

  RandomAccessFile(mp3, "rw").use { it.setLength(walk.lastFrameEnd) }
  return trailing
}

/** Offset just past an ID3v2 tag, or 0 when the file does not start with one. */
private fun id3v2End(reader: BlockReader): Long {
  if (reader.byteAt(0) != 0x49 || reader.byteAt(1) != 0x44 || reader.byteAt(2) != 0x33) return 0L

  val flags = reader.byteAt(5)
  if (flags < 0) return 0L

  var size = 0
  for (i in 6L..9L) {
    val b = reader.byteAt(i)
    if (b < 0) return 0L
    size = (size shl 7) or (b and 0x7F)
  }

  val footer = if ((flags and 0x10) != 0) 10 else 0
  return 10L + size + footer
}

/**
 * First offset at or after [from] that holds a frame header the next frame confirms.
 *
 * The confirmation matters: `0xFF 0xFB` occurs inside audio data, and a false sync near the
 * end of the file would report a shortfall that is not there.
 */
private fun syncFrom(reader: BlockReader, from: Long, length: Long): Long? {
  var pos = maxOf(from, 0L)
  while (pos + 4 <= length) {
    val frameLength = frameLengthAt(reader, pos)
    if (frameLength != null) {
      // A frame that reaches or passes the end of the file is the last one, whole or not.
      if (pos + frameLength >= length || frameLengthAt(reader, pos + frameLength) != null) return pos
    }
    pos++
  }
  return null
}

/** Declared length in bytes of the Layer III frame at [pos], or null when it is not one. */
private fun frameLengthAt(reader: BlockReader, pos: Long): Int? {
  val b0 = reader.byteAt(pos)
  val b1 = reader.byteAt(pos + 1)
  val b2 = reader.byteAt(pos + 2)
  val b3 = reader.byteAt(pos + 3)
  if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) return null

  if (b0 != 0xFF || (b1 and 0xE0) != 0xE0) return null

  val version = (b1 shr 3) and 0x03
  if (version == 1) return null                     // reserved
  if (((b1 shr 1) and 0x03) != 1) return null       // Layer III only
  if ((b3 and 0x03) == 2) return null               // reserved emphasis

  val bitrateIndex = (b2 shr 4) and 0x0F
  if (bitrateIndex == 0 || bitrateIndex == 15) return null

  val rateIndex = (b2 shr 2) and 0x03
  if (rateIndex == 3) return null

  val mpeg1 = version == 3
  val bitrate = (if (mpeg1) MPEG1_BITRATES else MPEG2_BITRATES)[bitrateIndex] * 1000
  val sampleRate = SAMPLE_RATES[version][rateIndex]
  val padding = (b2 shr 1) and 0x01

  return (if (mpeg1) 144 else 72) * bitrate / sampleRate + padding
}

/** True when the bytes at [pos] start an ID3v1 tag or an ID3v2 footer. */
private fun hasKnownTrailer(mp3: File, pos: Long): Boolean =
  RandomAccessFile(mp3, "r").use { raf ->
    val marker = ByteArray(3)
    raf.seek(pos)
    if (raf.read(marker) < 3) return false
    val text = String(marker, Charsets.US_ASCII)
    text == "TAG" || text == "3DI"
  }

/**
 * Absolute byte reads over a buffered window. The walk moves forward, so nearly every read
 * lands in the current block.
 */
private class BlockReader(private val raf: RandomAccessFile, private val length: Long) {
  private val buffer = ByteArray(1 shl 16)
  private var start = -1L
  private var filled = 0

  /** The unsigned byte at [pos], or -1 past the end of the file. */
  fun byteAt(pos: Long): Int {
    if (pos < 0L || pos >= length) return -1

    if (start < 0L || pos < start || pos >= start + filled) {
      raf.seek(pos)
      start = pos
      filled = maxOf(raf.read(buffer), 0)
      if (filled == 0) return -1
    }

    return buffer[(pos - start).toInt()].toInt() and 0xFF
  }
}
