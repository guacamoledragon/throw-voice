# Handoff: pawa mp3 files have no Xing/VBR header

Written 2026-08-04. Investigation done from the `pawa-companion` iOS app repo, which
consumes these recordings. No files in this repo were modified — this is a read-only
findings document.

## TL;DR

Production mp3s contain a **zeroed-out placeholder** where the Xing/LAME VBR header frame
should be. Because the files are VBR with no header, every player extrapolates duration and
seek position from the first frame's bitrate and gets both wrong — a real 56:28 recording
reports as 70:10, and playback position drifts ~6% fast.

The code that would write the header **is deployed** and is silently doing nothing.

A one-command remux fixes it completely, preserves VBR, and costs 333 bytes:

```
ffmpeg -i in.mp3 -c copy -write_xing 1 out.mp3
```

## How this surfaced

A companion iOS app plays these recordings and transcribes them with Apple's on-device
`SpeechAnalyzer`. The transcript timestamps and the audio drifted apart badly — by ~11
seconds at the 3-minute mark. Initial suspicion was the transcriber. It was not: Apple's
analyzer reported the duration exactly right. The player was wrong, because the file told
it the wrong thing.

## Verified measurements

Test file (real production recording, 50,030,424 bytes):

```
https://download.pawa.im/prod/1425922053059379363/01KZ4G31PV5D4839JFRRWEE6A6.mp3
```

| Measurement | Value |
|---|---|
| `Xing` / `Info` header frames in file | **0** |
| ffprobe duration (header-derived estimate) | 4210.78 s = **70:10** |
| Actual full decode | 3388.49 s = **56:28** |
| Apple `SpeechAnalyzer` reported duration | **56:28** (matches true decode) |
| Overestimate | **24%** |
| Observed playback drift in the app | ~6% fast (11 s by the 3:00 mark) |

Reproduce:

```bash
curl -sL -o big.mp3 'https://download.pawa.im/prod/1425922053059379363/01KZ4G31PV5D4839JFRRWEE6A6.mp3'
strings -a big.mp3 | grep -cE '^(Xing|Info)'                                  # → 0
ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 big.mp3  # → 4210.78
ffmpeg -i big.mp3 -f null - 2>&1 | grep -oE 'time=[0-9:.]+' | tail -1         # → 00:56:28.48
```

### The file literally contains the un-overwritten placeholder

First 16 bytes of the production file:

```
00000000: fffb 9464 0000 0000 0000 0000 0000 0000
          ^^^^^^^^^ valid MPEG-1 Layer III frame header, then 380 zero bytes
```

`ff fb 94 64` decodes to MPEG-1 Layer III, 128 kbps, 48 kHz, joint stereo → frame size 384
bytes. Real audio resumes at offset 384. That is exactly jump3r's computed
`VBR_seek_table.TotalFrameSize` for the placeholder. So `strings` finds no `Xing` not
because the frame is missing, but because it is **still all zeros**.

## A wrong theory, recorded so nobody re-derives it

The first hypothesis was that the file is thousands of concatenated mp3 streams, based on
`LAME3.98.4` appearing **8541 times**. **This is wrong.** A control file — a single
continuous 24.78 s mp3 from one LAME invocation — contains 137 occurrences of `LAME3`.

Normalized: control 5.5 hits/sec, production file 2.5 hits/sec. Same order of magnitude.

The recurring string is LAME writing its version into **per-frame ancillary data**. It is
not a stream counter. The production file is one continuous LAME bitstream. There is no
concatenation problem.

## It is VBR, definitively

`BaseAudioRecorder.kt:105-111` constructs
`LameEncoder(format, BITRATE=128, CHANNEL_MODE_AUTO, QUALITY_HIGHEST, vbr)` where
`vbr = BOT_MP3_VBR`. In `LameEncoder.nInitParams` the `bitrate` argument is **ignored when
VBR is on** — it sets `gfp.VBR = vbr_default; gfp.VBR_q = quality`. With
`QUALITY_HIGHEST = 1` this is effectively **LAME -V1**. The `BITRATE = 128` constant is
dead code in this configuration.

Confirmed by parsing 189 real frame headers from the production file — bitrates range
32→320 kbps:

```
{128:33, 32:25, 160:38, 112:50, 80:7, 96:23, 320:2, 256:2, 64:1, 192:6, 56:2}
```

The quiet opening frames are 32 kbps. Players sample the start, infer ~95 kbps, and compute
`filesize / 95kbps` → 70:10 instead of 56:28. That ratio is the drift.

## Root cause

`de.sciss:jump3r:1.0.5` (`pom.xml:99-103`) — a pure-Java transliteration of LAME 3.98.4.
No native LAME, no ffmpeg in the encode path.

`Lame.lame_init_bitstream` calls `VBRTag.InitVbrTag`, which writes a placeholder:

```java
// write dummy VBR tag of all 0's into bitstream
byte buffer[] = new byte[MAXFRAMESIZE];
setLameTagFrameHeader(gfp, buffer);
int n = gfc.VBR_seek_table.TotalFrameSize;
for (int i = 0; i < n; ++i) bs.add_dummy_byte(gfp, buffer[i] & 0xff, 1);
```

Only `VBRTag.putVbrTag(gfp, RandomAccessFile)` — which seeks to offset 0 and overwrites
those bytes — turns the placeholder into a real header. That step is what's failing.

### The fix IS deployed, and silently does nothing

Production runs `VERSION=2.17.0-8b1c9af0` (a master build; CI tags master builds
`${BOT_VERSION}-${sha}`).

```
8b1c9af  2026-07-16  Patch JDA Decoder to fix SIGSEGV race on decode-after-close
4b83226  2026-04-12  Write VBR header when finalizing MP3 file
git merge-base --is-ancestor 4b83226 8b1c9af0  →  YES (prod contains the fix)
```

Note: the newest *release tag* is v2.17.0 (2026-03-03), which does **not** contain
`4b83226`. So "the fix was never released" is true of tags but **false of what's deployed**.
Don't be misled by the tag history.

The call site, `BaseAudioRecorder.kt:272-274`:

```kotlin
if (vbr && fileFormat == "mp3") {
  lameEncoder?.let { writeVbrTag(it, recordingFile) }
}
```

And `Utils.kt:57-71`:

```kotlin
fun writeVbrTag(encoder: LameEncoder, mp3: File) {
  // ... reflection to get private `gfp` and package-private `vbr` ...
  RandomAccessFile(mp3, "rw").use { raf ->
    vbrTag.putVbrTag(gfp, raf)          // ← return value discarded
  }
  logger.info { "Wrote VBR/Xing header to $mp3" }   // ← logged unconditionally
}
```

### Why checking the return value would NOT have caught this

`getLameTagFrame` has three guards:

```java
if (!gfp.bWriteVbrTag)            return 0;   // ruled out — the placeholder exists,
                                              //   so this was true at init
if (gfc.Class_ID != Lame.LAME_ID) return 0;   // encoder already closed
if (gfc.VBR_seek_table.pos <= 0)  return -1;  // no frames in the seek table
```

and `putVbrTag` maps them like this:

```java
int bytes = getLameTagFrame(gfp, buffer);
if (bytes > buffer.length) return -1;
if (bytes < 1)             return 0;   // ← nothing written, reported as 0
stream.write(buffer, 0, bytes);
return 0;                              // ← success, also 0
```

**`putVbrTag` returns `0` for both success and "no tag frame produced".** Only the
`pos <= 0` path returns `-1`. So a `Class_ID` mismatch looks identical to success. Logs
almost certainly show "Wrote VBR/Xing header to …" on every recording while producing
headerless files.

### NOT yet proven — pick this up here

Which of the two `return 0` guards actually fires was **not** determined. Reflection targets
are fine (`gfp` at `LameEncoder.java:129`, `vbr` at `:243`), and `writeVbrTag` is not
throwing — the outer `try` in `processCompletedRecording` would have skipped the upload, and
uploads succeed.

`encodeFinish` only calls `lame_encode_flush`; `close()` is separate
(`BaseAudioRecorder.kt:346`, inside `cleanup()`), and `cleanup()` awaits
`processingExecutor` termination first — so on the *normal* path the encoder should still be
open. But `disconnect()` has an early-return path (upload timeout, `:324` comment) that
skips `cleanup()`, and the locking around `recordingLock` is non-obvious. **A race where the
encoder is closed before `writeVbrTag` runs has not been ruled out.**

To settle it: log the byte count from `getLameTagFrame` rather than `putVbrTag`'s return, or
just read back the first 4 bytes after writing and assert they aren't zero.

Also: the file has **no ID3v2 header at offset 0 and no ID3v1 `TAG` at EOF**, so
`addCommentToMp3` (`Utils.kt:35-50`) is also silently failing — almost certainly
jaudiotagger throwing `InvalidAudioFrameException` on the zeroed first frame, which that
function catches and logs.

## The recommended fix, validated on the real file

```bash
ffmpeg -i big.mp3 -c copy -write_xing 1 fixed-vbr.mp3
```

| File | Size | Xing | Header estimate | True decode |
|---|---|---|---|---|
| original | 50,030,424 | 0 | **70:10** ❌ | 56:28.48 |
| remuxed | 50,030,757 | 1 | **56:28.49** ✅ | 56:28.47 |

**+333 bytes.** `-c copy` means audio frames are byte-identical — VBR fully preserved,
nothing re-encoded, no quality change, no meaningful size cost. This was run and verified,
not theorized.

### It fixes duration, but NOT seek accuracy

Confirmed on device 2026-08-04. After the remux the app reports the correct `56:28`, but
tapping around to seek still lands in the wrong place — player reported `0:32` while the
audio playing was the segment at `~0:42`, roughly 10 s off.

This is not a residual bug. It is the ceiling of the format:

| | Seek index entries | Resolution across 56:28 |
|---|---|---|
| Xing TOC (mp3) | **100** | ~34 s per entry, linearly interpolated |
| m4a sample table | **158,837** | ~21 ms per entry, exact offsets |

The Xing TOC is a fixed 100-byte table mapping percentage → byte offset. Players interpolate
linearly *within* a bucket. On VBR content where bitrate swings 32→320 kbps, bytes do not map
linearly to time inside a bucket, so multi-second error is expected. A ~10 s error inside a
34 s bucket is exactly what the format predicts.

**Implication:** if accurate seeking matters — and for a transcript UI where tapping a line
should jump to that moment, it does — then repairing the mp3 header is necessary but not
sufficient. See the AAC/m4a option below, which is now a stronger candidate than it first
appeared.

### Why post-processing beats repairing the encoder path

- Covers `Pawa.recoverRecording` (`Pawa.kt:155-170`), which calls `queueFileIntoMp3` with
  **no encoder in scope** and therefore can never write a Xing tag from Kotlin. That hole is
  permanent otherwise.
- Removes reflection into a 15-year-old library's private fields.
- Kills a corruption hazard: `SharedAudioRecorder.handleSizeLimit:66-73` evicts
  `queue.peek()` past 256 MB — the record holding the placeholder frame. If `putVbrTag` ever
  *does* succeed on such a recording, it overwrites 384 bytes of **real audio** at offset 0.
- Same command repairs the existing archive (see Backfill).
- Verifiable — you can assert a Xing frame exists and fail loudly. Current code cannot
  distinguish success from failure at all.

### Suggested shape

```
queueFileIntoMp3 → ffmpeg remux to temp → assert Xing present → atomic rename
  → addCommentToMp3 → upload
```

Caveats:

- Run the remux **before** `addCommentToMp3`; ffmpeg rewrites the container and would drop
  the tag. (Bonus: tagging likely starts working once the first frame is valid.)
- Add `-map_metadata -1 -fflags +bitexact` to suppress ffmpeg's `TSSE: Lavf` tag.
- Needs a temp file + atomic rename, and briefly 2× disk for the recording.
- Adds an ffmpeg binary to the container image and one full read+write pass before upload
  (~1 s for 50 MB).
- Delete `writeVbrTag` and its call site rather than leaving dead reflection in place.

## Backfill

**Existing recordings are fixable without re-encoding.** The remux rebuilds the Xing frame
with the true frame count and TOC from the existing frames and copies the audio payload
byte-for-byte. A batch job over the S3 bucket restores duration and seek accuracy for the
whole archive at no quality cost.

Fixing only the encoder path repairs nothing already stored.

## Alternatives considered

- **CBR instead of VBR** — rejected. Was only ever built as a diagnostic control. VBR with a
  proper header is completely valid; the header is 384 bytes. CBR would inflate every file
  for no benefit.
- **AAC in m4a** — **now the leading candidate if seek accuracy matters**, because it is the
  only option that actually delivers it (see "It fixes duration, but NOT seek accuracy").
  Verified on a converted copy of the production file: 158,837 per-sample index entries,
  `duration_ts=162647424 @ 1/48000` = 3388.488 s exactly. Costs: jump3r is mp3-only, so this
  means a new encoder dependency plus changes to `BOT_FILE_FORMAT`, S3 keys, the Discord
  attachment flow, and the iOS client. Largest blast radius of any option.

  If you go this route, **`-movflags +faststart` is mandatory**. ffmpeg's default writes
  `moov` *after* `mdat` (verified: offset 37,665,852 of 38,302,523), forcing a player to
  fetch the index from the end of the file before it can start. With faststart, `moov` lands
  at offset 28.

  Conversion is a re-encode, not a remux, so unlike the mp3 fix it is lossy and slow — a
  consideration for backfilling the archive.
- **Client-side duration override** — the DB already stores an accurate duration
  (`durationCounter.get() * 20L`, `BaseAudioRecorder.kt:81`, persisted at
  `SharedAudioRecorder.kt:112`/`:132`), counted from real 20 ms Discord frames and independent
  of the mp3 header. The app could display that. But it does **not** fix seek drift, which
  needs a real Xing TOC. Stopgap only.

## Open questions

1. Which `getLameTagFrame` guard actually fires? (See "NOT yet proven" above.)
2. ~~Is ffmpeg's rebuilt Xing TOC accurate enough for precise seeking?~~ **ANSWERED: no.**
   Duration becomes exact; seeking is still ~10 s off on a 56-minute file. The 100-entry TOC
   is too coarse and the error is inherent to the format, not to ffmpeg. See "It fixes
   duration, but NOT seek accuracy". Remaining sub-question: is duration-only correctness
   good enough for pawa's use cases, or does the transcript UI require the m4a migration?
3. Are there recordings large enough to have hit `handleSizeLimit` eviction, and if so did
   any get 384 bytes of real audio overwritten?
4. Does `BOT_MP3_VBR` vary by deployment? All conclusions here assume VBR on, which the frame
   analysis confirms for this one file.

## Provenance

Everything in the "Verified measurements", "It is VBR", and "recommended fix" sections was
measured against the real production file with ffmpeg/ffprobe. Code line references were read
from this repo at `8b1c9af`. Items under "NOT yet proven" and "Open questions" are explicitly
unverified.
