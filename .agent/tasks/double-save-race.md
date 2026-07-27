# Task: Fix the double-save race that destroys recordings

Work in this repo (pawa, a Kotlin Discord voice-recording bot, Maven build) on a
new branch off master: `fix-double-save-race`.

## The bug

`BaseAudioRecorder.saveRecording` (`src/main/kotlin/tech/gdragon/listener/BaseAudioRecorder.kt:220`)
has no idempotence guard. It sets `isRecording.set(false)` at line 226 but never
gates on it, then unconditionally spawns `thread { processCompletedRecording(...) }`
at line 244. When two events invoke it concurrently for the same session, two
threads encode the same queue into the **same** `.mp3` path and upload to the
**same** S3 key.

Observed in production on 2026-07-25, session `01KYB9T6VPD3211Z7P8AEHQRQB`:

```
00:47:59.015 saveRecording started  (DefaultDispatcher-worker-3)
00:47:59.020 saveRecording started  (DefaultDispatcher-worker-1)   <- duplicate
             two encoder threads (Thread-7165, Thread-7166) run concurrently
00:49:00.285 Finished uploading file - (34 MB) 1507.../01KYB9T6....mp3   <- 7166, good
00:49:00.296 Successfully deleted local file 01KYB9T6....mp3            <- 7166 cleanup
00:49:00.741 Finished uploading file - (0 bytes) 1507.../01KYB9T6....mp3 <- 7165 OVERWRITES
             -> NoSuchFileException at Datastore.kt:137 -> "Error uploading recording"
```

Net effect: the good 34 MB object is **overwritten with a 0-byte object at the
same key**, the DB `url` already points there, and nothing is left on disk. The
user gets a working link to an empty file and there is no leftover `.mp3` to
signal the loss.

Scope over 2026-07-06 .. 2026-07-26 (Honeycomb, env `prod`, dataset `pawa`):
**9 sessions double-processed, 4 confirmed 0-byte overwrites**
(`01KXBDYZDW282KP6QC8KWZBA53`, `01KXYBJ40Q9ED7XHA0NPG5EQX3`,
`01KY5CW2JDWWNQYQS0WHNNRWN1`, `01KYB9T6VPD3211Z7P8AEHQRQB`). All 4
`java.nio.file.NoSuchFileException` upload errors in that window are this bug.

Callers that can race:
- `BotUtils.leaveVoiceChannel` — `src/main/kotlin/tech/gdragon/BotUtils.kt:199`
  (reached from concurrent voice-update / auto-stop events)
- `BetaSave` — `src/main/kotlin/tech/gdragon/commands/audio/BetaSave.kt:30`

## The fix

Claim the save exactly once. Replace the bare `isRecording.set(false)` at
`BaseAudioRecorder.kt:226` with a compare-and-set that returns early on the
losing call:

```kotlin
if (!isRecording.compareAndSet(true, false)) {
  logger.warn { "saveRecording already in progress, ignoring duplicate: $session" }
  return Pair(recordingRecord, Semaphore(1, true))
}
```

The returned semaphore must already have a permit available — the caller
(`BotUtils.leaveVoiceChannel:202` -> `recorder.disconnect(save, recording, recordingLock)`)
waits on it, and the duplicate call must not block for 60s and emit a spurious
`Upload did not finish within 60s` WARN.

Verify that returning `recordingRecord` (rather than `null`) on the losing path
is correct for every caller before you commit to it; if it is not, say so and
adjust.

Do not add locks, queues, or a dedup cache. One guard, one log line.

## Before you start

This brief is a problem statement, not a plan. Start with
`superpowers:brainstorming`, then `superpowers:writing-plans`, and produce the
plan before touching code.

The human who filed this is the project's main developer, is available, and
wants to be asked. Check in when: the evidence here does not match what you find
in the code, the fix looks materially bigger than this brief implies, or you are
about to change behaviour the brief does not mention. Do not guess and proceed on
any of those.

## Ground rules

- Never touch production (pawa.im, SSH, the prod DB). Local code and tests only.
  Do not deploy, tag, or edit `CHANGELOG.md`.
- Use `superpowers:test-driven-development`: write the test, RUN it, confirm it
  fails for the stated reason, then implement, then confirm it passes. Capture
  the real command output both times.
- Use `superpowers:verification-before-completion` before claiming anything is done.

## Required verification

1. A test that calls `saveRecording` twice (concurrently or back-to-back) on one
   recorder and asserts the upload/encode path runs exactly once. Existing tests
   live in `src/test/kotlin/tech/gdragon/listener/SharedAudioRecorderTest.kt` —
   follow their mocking style.
2. Confirm the pre-existing `SharedAudioRecorderTest` tests still pass unmodified.
   If any needs editing, explain why in your report rather than silently changing it.
3. Full suite: `mvn test`. `PawaTest` needs Docker for testcontainers; if Docker
   is unavailable, run everything else and flag `PawaTest` as not run — do not
   claim green without evidence.

## Report must contain

Fail -> pass test evidence (real trimmed output), the caller-contract check on the
early-return value, full-suite outcome, any deviation from this brief and why,
and anything you could not verify.

Commit with an imperative-mood message. Leave the branch unpushed and unmerged.
