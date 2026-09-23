# Task: Stop emitting mp3 files that end with an incomplete frame (#96)

Work item: https://gitlab.com/pawabot/pawa/-/work_items/96
Triage comment: https://gitlab.com/pawabot/pawa/-/work_items/96#note_3887254367
Branch: `mp3-tail-telemetry` (MR !146)

## Status

**Done.** Both fixes are on the branch. CI passed. The test bot showed the result for each of the
two paths. The work is ready for merge and a release.

## Root cause

`lame_encode_flush` in jump3r pads the last frame to its declared length.
`LameEncoder.encodeFinish` is its only caller. A recording that never gets to `encodeFinish` keeps
a final frame whose header declares more bytes than the encoder wrote.

ffmpeg copies that frame and counts it in the Xing header, so the declared sample count is more
than the data. Apple's `AVAudioFile` trusts that count, and `SpeechAnalyzer` fails on the last
packet. The companion app then shows `_GenericObjCError error 0` and no transcript.

Same PCM, same encoder, one difference:

| Run | Frames | Shortfall |
|---|---|---|
| With `encodeFinish` | 252 | **0** |
| Without `encodeFinish` | 249 | **21** |

## Two paths to that state

1. **The JVM stops during a recording.** `/recover` makes the mp3 again from the `.queue` file.
   The flush can never run, so this path always needs the tail repair.
2. **`saveRecording` read the queue before the flush got to it.** The result of
   `awaitTermination` was discarded. If the drain was slow, `processCompletedRecording` made the
   mp3 before `processAudioLoop` added the flush. This path explains the first report: a
   normal save, 4 bytes short.

## The fixes

| Commit | Change |
|---|---|
| `950b61b` | Fix 2. `remuxWithXingHeader` calls `trimIncompleteTrailingFrame` before ffmpeg. The ffmpeg output replaces the file only if it has a Xing header and `shortfall == 0`. |
| `731de18` | Adds the `drainTimeout` constructor parameter. The default stays at 10 seconds. |
| `dab9efb` | Adds a test for path 2. Before the fix, it failed with a shortfall of 163. |
| `f8b6a75` | Fix 1. The upload thread waits for the processing loop to end before it makes the mp3. A drain timeout now writes a warning to the log. |
| `87138a7` | The tail check and the trim are now inside the `try` block of the remux. An IO error from either one no longer stops an upload or `/recover`. |
| `2b3475d` | Tests that a read-only mp3 stays unchanged and that the remux does not throw. When the test runs as root, it is skipped. |

The fix in `remuxWithXingHeader` covers all three callers: `saveRecording` and the two
`/recover` paths in `Pawa.kt`.

## Verification

**Local:**

- The two tests that failed before now pass. Their assertions did not change.
- All 11 `Mp3FrameWalkTest` tests pass.
- The new tests in `SharedAudioRecorderTest` and `UtilsTest` pass.
- The full suite has 8 failures, in `DecoderRaceTest`, `PawaTest`, `S3DatastoreTest` and
  `DatabaseTest`. The same 8 fail on `master`. They need a Docker environment.
- The fixture below had a shortfall of 31. The remux trimmed 65 bytes, and the result has a
  shortfall of 0.

**Test bot, 2026-09-22 (`PAWA_REF=mp3-tail-telemetry` at `2b3475d`):**

| Test | Session | `pre-remux` shortfall | Trim | `post-remux` shortfall |
|---|---|---|---|---|
| Normal save, 38 s | `01M35VRKJJMAP3AKVG9K1QJXPR` | 0 | none | 0 |
| Killed at 1 min 41 s, then `/recover` | `01M35W31K2GPV2KK7A20NBKEJD` | 31 | 65 bytes | 0 |

The normal save had a clean tail before the remux, so fix 1 worked. The killed recording showed
the same numbers as the local fixture, so fix 2 worked. The log had no errors.

## Constraints for later changes

- Do not increase the drain timeout. It sets only how long the caller waits. It does not set
  when the queue is read.
- Do not change the tail assertions to `shouldBeGreaterThan(0L)`.
- Keep the ID3v1 and `3DI` guard in `trimIncompleteTrailingFrame`. `/recover` can run on a file
  that `addCommentToMp3` tagged before. Without the guard, the trim removes the tag.
- The `audio.mp3.*` MDC keys are a query contract. Do not change their names.

## Known issue, not fixed

`syncFrom` in `Mp3FrameWalk.kt` accepts a frame without the next-frame check if the frame
gets to the end of the file. After a resync, a false `0xFF 0xFx` pattern in the last 1 to
1.4 KB can then show a shortfall that is not real.

On the ffmpeg output, a false shortfall makes the remux keep the original file, and that file
has no Xing header. This needs bytes that are not frames in the stream. ffmpeg output normally
has none, so the risk is low. The code review of MR !146 found this issue.

## Real-world fixture (internal, not in git)

A recording made on the test bot and stopped during the session:

```
.agent/fixtures/96/01M3579CX0WK2Y8A0Y42JXGM4Z.queue
md5 bf9d50dd6fbdb970b770ae779d27d11f   1048576 bytes
```

`*.queue` and `*.mp3` are gitignored, so the file stays local. Reading it does not change it.
To walk it, make the classpath once and then run the script:

```sh
mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt -DincludeScope=runtime
cat > /tmp/walk.jsh <<'EOF'
var q = new java.io.File(".agent/fixtures/96/01M3579CX0WK2Y8A0Y42JXGM4Z.queue");
var out = new java.io.File("/tmp/recovered.mp3");
tech.gdragon.api.tape.UtilsKt.queueFileIntoMp3(q, out);
System.out.println(tech.gdragon.api.tape.Mp3FrameWalkKt.walkMp3Frames(out));
tech.gdragon.api.tape.UtilsKt.remuxWithXingHeader(out);
System.out.println(tech.gdragon.api.tape.Mp3FrameWalkKt.walkMp3Frames(out));
/exit
EOF
jshell --class-path "target/classes:$(cat /tmp/cp.txt)" --execution local -q /tmp/walk.jsh
```

Expected result:

```
Mp3Walk(audioStart=0, frameCount=6841, lastFrameEnd=845640, shortfall=31, resyncCount=0)
Mp3Walk(audioStart=20, frameCount=6842, lastFrameEnd=845852, shortfall=0, resyncCount=0)
```

## Environments (internal)

- **Production.** Docker context `pawa` → `ssh://pawa.im`, container `pawa_bot_1`. It runs
  `2.17.0-4312cbc2`, so it does not have this code yet. **Read only.**
- **Test bot.** Coolify on `sakura.local`. Use `docker -H ssh://sakura.local`. The container is
  `bot-xmxfo2ye3adyvhnaty21xxfw`. The image is distroless, so it has no shell. Use `docker cp`,
  not `docker exec ... sh`. Logs are at `/app/logs/app.json`, recordings at
  `/app/data/recordings`. The test guild id is `333055724198559745`.
- **Test bot version.** `PAWA_VERSION` and the image labels do not show the built commit. To
  find which code runs, examine the classes in `/app/pawa-dev.jar`.
- **Honeycomb.** Team `gdragon-d9`, dataset `pawa`, environments `prod` and `dev`. `honeytail`
  does not run on the test host, so no test-bot data gets to Honeycomb. The `audio.mp3.*`
  columns will show in `prod` after the release.

## Follow-ups, not part of this task

- Release, then measure the rates in Honeycomb. Group `Mp3 tail check` events by
  `audio.mp3.stage` and `audio.mp3.tail.shortfall`. Count the drain warnings
  (`Audio processing did not finish within`) to see how frequently path 2 occurs.
- Walk the 1,041 leftover `.queue` files on the production host through the nREPL to get a
  historical rate. One `.queue` leaks per recording, so they are an unbiased sample of about
  four days. This needs its own session.
- The `.queue` leak itself. A saved recording keeps its `.queue`, so `/recover` offers sessions
  that were already uploaded.
- Fix the `syncFrom` issue above if a false shortfall shows in the `prod` data.
