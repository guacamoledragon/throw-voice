# Task: Stop emitting mp3 files that end with an incomplete frame (#96)

Work item: https://gitlab.com/pawabot/pawa/-/work_items/96
Triage comment (read this first): https://gitlab.com/pawabot/pawa/-/work_items/96#note_3887254367

Continue on the existing branch `mp3-tail-telemetry` (MR !146). The groundwork and the two
failing tests are already there. Do not start from `master`.

The cause is known and reproducible. **Two tests fail. Make them pass without weakening them.**

## Root cause

`lame_encode_flush` in jump3r is documented to "pad with ancillary data so last frame is
complete". `LameEncoder.encodeFinish` is its only caller. A recording that never reaches
`encodeFinish` keeps a final frame whose header declares more bytes than the encoder emitted.
ffmpeg then copies that frame and counts it in the Xing header, so the declared sample count
exceeds the data. Apple's `AVAudioFile` trusts that count and `SpeechAnalyzer` fails on the
last packet, which the companion app shows as `_GenericObjCError error 0` and no transcript.

Same PCM, same encoder, one difference:

| Run | Frames | Shortfall |
|---|---|---|
| With `encodeFinish` | 252 | **0** |
| Without `encodeFinish` | 249 | **21** |

## Two paths reach that state

1. **The JVM dies during a recording.** `/recover` rebuilds the mp3 from the `.queue` file.
   The flush can never run, so this path always needs the tail repair.
2. **`saveRecording` reads the queue before the flush lands.**
   `BaseAudioRecorder.kt:235` calls `processingExecutor.awaitTermination(10, SECONDS)` and
   throws the result away. If the drain is not finished, `processCompletedRecording` assembles
   the mp3 while `processAudioLoop` has not yet added the flush entry. No crash is needed.
   This path explains the originally reported file: an ordinary 9:10 save, uploaded correctly,
   4 bytes short.

That timeout is silent. The `logger.warn { "Audio processing didn't complete in time" }` sits
inside `catch (e: InterruptedException)`, so a real timeout logs nothing.

## Start here

```sh
mvn -Dtest='UtilsTest,Mp3FrameWalkTest' -DfailIfNoTests=false test
```

```
UtilsTest > remux of a clipped mp3 produces a file that ends on a frame boundary
  expected:<0L> but was:<4L>
UtilsTest > recovering a recording that never flushed produces a file ending on a frame boundary
  expected:<0L> but was:<64L>
```

Both assert their precondition first, so a failure means the requirement is unmet, not that the
fixture is wrong. `Mp3FrameWalkTest` has 11 tests and they all pass. Keep them passing.

## What already exists — do not rewrite it

- `src/main/kotlin/tech/gdragon/api/tape/Mp3FrameWalk.kt` — `walkMp3Frames`,
  `trimIncompleteTrailingFrame`, `Mp3Walk.loggingFields`. Covered by the 11 tests.
- `trimIncompleteTrailingFrame` is finished and tested but **deliberately never called**.
- `remuxWithXingHeader` logs `audio.mp3.*` MDC fields before and after the ffmpeg step.
  Measurement only. It repairs nothing.
- `UtilsTest.encodeVbrIntoQueueWithoutFlush` reproduces what a killed recorder leaves on disk.
  It encodes noise, not silence, so VBR frame lengths vary the way speech makes them vary.

## The two fixes

**Fix 1 — make the flush land.** Use the `awaitTermination` result instead of discarding it,
and do not assemble the mp3 until the processing loop has finished.

A deterministic test needs a seam, because the 10 seconds is a literal. Add a constructor
parameter beside the existing `uploadWaitTimeout`, for example
`drainTimeout: Duration = DEFAULT_DRAIN_WAIT`. A test can then set it very low and assert that
the produced mp3 still ends on a frame boundary.

**Fix 2 — repair the tail.** Call `trimIncompleteTrailingFrame` before the ffmpeg step, and
replace the `hasXingOrInfoHeader` guard with one that also requires `shortfall == 0` on the
ffmpeg output. `remuxWithXingHeader` has three callers (`BaseAudioRecorder.kt:272`,
`Pawa.kt:163`, `Pawa.kt:171`), so putting the repair inside that function covers all of them.

## Do not

- Do not widen the 10-second timeout. That hides the fault instead of removing it.
- Do not relax either failing assertion to `shouldBeGreaterThan(0L)`. An earlier version of the
  first test did exactly that as a characterization test, and it was replaced on purpose.
- Do not drop the ID3v1 and `3DI` guard in `trimIncompleteTrailingFrame`. `/recover` can run on
  a file that `addCommentToMp3` already tagged, and without the guard the trim eats the tag.

## Real-world fixture (internal, not in git)

A recording made on the test bot and killed mid-session, kept for this task:

```
.agent/fixtures/96/01M3579CX0WK2Y8A0Y42JXGM4Z.queue
md5 bf9d50dd6fbdb970b770ae779d27d11f   1048576 bytes
```

`*.queue` and `*.mp3` are gitignored, so the file stays local. Reading it does not modify it.

Rebuild the classpath once, then walk it:

```sh
mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt -DincludeScope=runtime
cat > /tmp/walk.jsh <<'EOF'
var q = new java.io.File(".agent/fixtures/96/01M3579CX0WK2Y8A0Y42JXGM4Z.queue");
var out = new java.io.File("/tmp/recovered.mp3");
tech.gdragon.api.tape.UtilsKt.queueFileIntoMp3(q, out);
System.out.println(tech.gdragon.api.tape.Mp3FrameWalkKt.walkMp3Frames(out));
/exit
EOF
jshell --class-path "target/classes:$(cat /tmp/cp.txt)" --execution local -q /tmp/walk.jsh
```

Expected today:

```
Mp3Walk(audioStart=0, frameCount=6841, lastFrameEnd=845640, shortfall=31, resyncCount=0)
```

After fix 2, the same file through `remuxWithXingHeader` must report `shortfall=0`. Before the
fix it reports 31 at both stages, and the trim removes 65 bytes.

## Environments (internal)

- **Production.** Docker context `pawa` → `ssh://pawa.im`, container `pawa_bot_1`. It runs
  `2.17.0-4312cbc2`, so none of this code is deployed there. **Read only.** The sandbox blocks
  production reads until the user approves each one.
- **Test bot.** Coolify on `sakura.local`. Use `docker -H ssh://sakura.local`. The container is
  `bot-xmxfo2ye3adyvhnaty21xxfw`. The image is distroless, so there is no shell: use
  `docker cp`, never `docker exec ... sh`. Logs are at `/app/logs/app.json`, recordings at
  `/app/data/recordings`. Test guild id `333055724198559745`.
- **Honeycomb.** Team `gdragon-d9`, dataset `pawa`, environments `prod` and `dev`. `honeytail`
  ships `logs/app.json` and it does **not** run on the test host, so nothing from the test bot
  reaches Honeycomb. The `audio.mp3.*` columns do not exist in either environment yet. See the
  Honeycomb section in `AGENTS.md`.

## Verification checklist

- Both previously failing tests pass.
- All 11 `Mp3FrameWalkTest` tests still pass.
- The fixture above reports `shortfall=0` after the remux.
- Eight tests fail on `master` too, in `DecoderRaceTest`, `PawaTest`, `S3DatastoreTest` and
  `DatabaseTest`. They need a working Docker environment and are unrelated. Compare against a
  `master` baseline before blaming your change.

## Optional follow-ups, not part of this task

- Deploy the branch to production to measure the forward rate, including how often the silent
  drain timeout in path 2 actually bites.
- Walk the 1,041 leftover `.queue` files on the production host through the nREPL for a
  historical rate. One `.queue` leaks per recording whether the save succeeded or not, so they
  are an unbiased sample of about four days. This needs its own session.
