# CombinedAudioRecorderHandler Audit & Refactoring Plan

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Architecture Overview](#architecture-overview)
3. [Bug Audit](#bug-audit)
4. [Standalone vs Shared Branching Inventory](#standalone-vs-shared-branching-inventory)
5. [The Lockup Problem — Root Cause Analysis](#the-lockup-problem)
6. [Refactoring Plan: Splitting CARH](#refactoring-plan)
7. [Testability Plan](#testability-plan)

---

## Executive Summary

`CombinedAudioRecorderHandler` (CARH) is a 522-line monolithic class that serves as the
audio recording engine for both PawaLite (standalone) and Pawa (shared). It mixes two
deployment modes via `if (standalone)` branches scattered throughout, uses a complex RxJava
pipeline for audio processing, and employs a fragile Semaphore-based synchronization scheme
for save/disconnect operations.

A refactored alternative already exists — `BaseAudioRecorder` with `StandaloneAudioRecorder`
and `SharedAudioRecorder` subclasses — accessible via the "Beta" commands (`BetaRecord`,
`BetaSave`). This refactoring replaces RxJava with a `BlockingQueue` + single-threaded
executor, which is far simpler and eliminates several classes of bugs.

**However, the production code path (non-beta) still uses CARH.** The `BotUtils.leaveVoiceChannel()`
hard-casts to `CombinedAudioRecorderHandler` at line 205, meaning the beta recorders cannot
be used with the standard `/stop` or `/save` commands — they crash with a `ClassCastException`.

### Key findings:

- **11 bugs/issues** identified in CARH (3 high severity)
- **1 probable root cause** for the lockup: blocking `uploadFile().complete()` + Semaphore
  deadlock inside the RxJava callback chain
- The beta refactoring (`BaseAudioRecorder`) is a **significant improvement** but has its
  own issues (thread-unsafe `speakers` set, `disconnect` double-release bug)
- **Zero tests** exist for any recording handler

---

## Architecture Overview

### Audio Flow (CARH — production path)

```
JDA Audio Thread (20ms frames)
    │
    ▼
handleCombinedAudio()
    │ durationCounter++ (non-atomic!)
    │ recording.speakers += users (non-thread-safe Set!)
    │
    ▼
PublishSubject.onNext(combinedAudio)
    │
    ▼ [RxJava chain, starts on computation scheduler]
doOnNext { isAfk() }         ← side effect: may spawn thread to leave channel
    │
    ▼
map { getAudioData(volume) }  ← extract PCM bytes
    │
    ▼
buffer(200ms, max 8)          ← batch into groups of up to 8
    │
    ▼
flatMap { encode to mp3 }     ← LAME encoding
    │
    ▼
doOnNext { size limit check } ← may send Discord message
    │
    ▼ observeOn(Schedulers.io())
collectInto(queueFile)        ← write to tape QueueFile
    │                           ← may evict old data (non-standalone)
    │
    ▼ [on subject.onComplete()]
Single emits queueFile
    │
    ▼
subscribe callback             ← uploads recording, releases Semaphore
```

### Audio Flow (BaseAudioRecorder — beta path)

```
JDA Audio Thread (20ms frames)
    │
    ▼
handleCombinedAudio()
    │ durationCounter.incrementAndGet() (AtomicLong ✓)
    │ recording.speakers.addAll() (still non-thread-safe ✗)
    │
    ▼
LinkedBlockingQueue.offer(AudioData)  ← bounded (2000), drops oldest if full
    │
    ▼ [Single-threaded executor: "audio-processor-$session"]
processAudioLoop()
    │ poll(100ms) from queue
    │ shouldProcessAudio() → subclass hook (AFK check)
    │ encodeAndWriteAudio()
    │   ├─ handleSizeLimit() → subclass hook (eviction)
    │   ├─ queue.add(bytes)
    │   └─ onAudioDataWritten() → subclass hook (size tracking)
    │
    ▼ [on saveRecording()]
processingExecutor.shutdown()
awaitTermination(10s)
    │
    ▼ [new thread]
processCompletedRecording()
    │ queueFileIntoMp3()
    │ uploadRecording() → subclass hook
    │
    ▼ finally
Semaphore.release()
```

The beta path is **dramatically simpler**: no RxJava, no hot Observable subscriptions, no
re-subscribing to Singles, no double-complete of subjects.

---

## Bug Audit

### BUG-1 [HIGH]: Semaphore deadlock in `saveRecording()` — probable lockup cause

**File:** `CombinedAudioRecorderHandler.kt:271-339`

```kotlin
fun saveRecording(...): Pair<Recording?, Semaphore> {
    canReceive = false
    val recordingLock = Semaphore(1, true)
    recordingLock.acquire()                    // permits = 0

    val disposable = single.subscribe { queueFile, _ ->  // BiConsumer
        // ... processing ...
        try {
            uploadRecording(...)                // calls BotUtils.uploadFile() — BLOCKING
        } finally {
            recordingLock.release(1)           // only release path
        }
    }

    subject.onComplete()                       // triggers the chain
    recordingLock.acquire()                    // BLOCKS until callback releases
    return Pair(recording, recordingLock)
}
```

**Problems:**

1. **The `subscribe` callback uses `BiConsumer<RecordingQueue?, Throwable?>`**. The error
   parameter is discarded (`_`). If the Single errors, `queueFile` is `null`, and the code
   dereferences it (`queueFile.let { ... }`) **outside** the try/catch/finally block → NPE
   → `recordingLock.release()` never runs → **permanent deadlock**.

2. **`uploadRecording()` calls `BotUtils.uploadFile()` which uses `.complete()`** — a
   synchronous, blocking JDA call. If Discord is rate-limited, slow, or the network is
   congested, this blocks the RxJava IO thread. The semaphore is held until upload finishes.
   Meanwhile, the calling thread (likely JDA's event dispatch thread or coroutine dispatcher)
   is blocked on `recordingLock.acquire()`. If this is the only event-processing thread,
   **all subsequent commands to the bot are blocked**.

3. **`datastore.upload()` (S3Datastore) uses `Failsafe` with retry + backoff (2-30s)**,
   adding more potential blocking time.

4. **Re-subscription to hot source**: `single.subscribe()` in `saveRecording()` creates a
   second subscription to the same `PublishSubject`-derived chain. The first subscription
   (from `createRecording()` constructor) is still active. Both fire when `subject.onComplete()`
   is called. The second subscription sees an already-completed subject, so `collectInto`
   emits the shared `queueFile` reference immediately. This works by accident (same file
   object on disk) but is fragile.

**Impact:** This is the most likely cause of the lockup. Under load, multiple guilds may
have recordings completing simultaneously. If Discord rate-limits or the S3 upload retries,
the calling threads block on semaphores, exhausting JDA's thread pool.

---

### BUG-2 [HIGH]: `RecordingDisposable` is not thread-safe

**File:** `CombinedAudioRecorderHandler.kt:48-72`

```kotlin
class RecordingDisposable {
    private var compositeDisposable: CompositeDisposable? = null

    fun add(disposable: Disposable) {
        if (compositeDisposable == null) {              // check
            compositeDisposable = CompositeDisposable() // create
        }
        compositeDisposable?.add(disposable)            // use
    }

    fun dispose() {
        compositeDisposable?.dispose()
        reset()
    }
}
```

**Problem:** Classic check-then-act race. `add()` is called from the constructor thread and
from `saveRecording()` (potentially a different thread). Two threads could both see `null`,
both create `CompositeDisposable`, and one's reference is lost — along with its disposables.

Additionally, `dispose()` sets `compositeDisposable = null` via `reset()`, but `add()` could
be called concurrently after `dispose()`, re-creating it without knowing disposal happened.

---

### BUG-3 [HIGH]: `disconnect()` can deadlock independently

**File:** `CombinedAudioRecorderHandler.kt:426-493`

```kotlin
fun disconnect(dispose: Boolean = true, recording: Recording? = null, recordingLock: Semaphore? = null) {
    canReceive = false

    if (dispose) {
        compositeDisposable.dispose()       // disposes existing subscriptions
    }

    single                                  // re-subscribe to same Single
        .doOnError { ... }
        .subscribe { queueFile, _ ->
            // cleanup: close queueFile, delete files
            recordingLock?.release(1)       // release if lock provided
        }

    subject.onComplete()                    // may be second call (no-op on subject)
    recordingLock?.let { lock ->
        lock.acquire(1)                     // BLOCKS until cleanup subscription releases
    }
}
```

**Problem:** When called after `saveRecording()` with `dispose=false`:
- `saveRecording` already called `subject.onComplete()`, so the subject is completed
- `disconnect` subscribes to `single` again (third subscription total)
- Since subject is already completed, the new subscription's chain immediately completes
- `collectInto` emits the shared `queueFile` immediately
- The callback runs cleanup and releases the lock

This *usually* works, but if the `single.subscribe` callback throws (e.g., `queueFile` is
already closed from `saveRecording`'s callback), the lock is never released → deadlock.

When called with `dispose=true` (no prior save):
- Existing subscriptions are disposed
- New subscription starts, subject completes immediately
- If `recordingLock` is null (the `!save` path), no blocking occurs — this path is safe

**Additional issue:** When `dispose=true`, `compositeDisposable.dispose()` is called but the
NEW `single.subscribe()` disposable is never added to any composite — it's a fire-and-forget
subscription that could leak.

---

### BUG-4 [MEDIUM]: Non-thread-safe mutable state accessed from multiple threads

**File:** `CombinedAudioRecorderHandler.kt:112-121`

| Field | Type | Written from | Read from | Risk |
|-------|------|-------------|-----------|------|
| `canReceive` | `Boolean` | `saveRecording`/`disconnect` (event thread) | `canReceiveCombined()` (JDA audio thread) | Visibility |
| `afkCounter` | `Int` | `isAfk()` (RxJava computation scheduler) | Same | Low (single writer) |
| `durationCounter` | `Long` | `handleCombinedAudio()` (JDA audio thread) | `duration` getter (any thread) | Lost updates, torn reads |
| `recordingSize` | `Long` | `collectInto` callback (RxJava IO thread) | `doOnNext` (computation thread) | Torn reads on 64-bit |
| `limitWarning` | `Boolean` | `doOnNext` (computation thread) | Same | Low |
| `silencedUsers` | `MutableSet<Long>` | `silenceUser()` (event thread) | `includeUserInCombinedAudio()` (JDA audio thread) | ConcurrentModificationException |

The beta `BaseAudioRecorder` fixes `durationCounter` (uses `AtomicLong`) and `isRecording`
(uses `AtomicBoolean`), but still has the `silencedUsers` and `speakers` issues.

---

### BUG-5 [MEDIUM]: `Recording.speakers` is not thread-safe

**File:** `DataAccessObject.kt` (Recording entity)

```kotlin
var speakers: MutableSet<User> = mutableSetOf()  // LinkedHashSet — NOT thread-safe
```

Written every 20ms from `handleCombinedAudio()` (JDA audio thread):
```kotlin
recording?.let { it.speakers += combinedAudio.users }
```

Read during save (RxJava IO thread or processing thread):
```kotlin
addCommentToMp3(recordingFile, recording?.speakers?.joinToString(...))
```

This can throw `ConcurrentModificationException` when iterating `speakers` for the MP3
comment while the JDA audio thread is still adding users (even though `canReceive` is false,
there could be in-flight `handleCombinedAudio` calls).

**Fix:** Use `ConcurrentHashMap.newKeySet()` or take a snapshot before iterating.

---

### BUG-6 [MEDIUM]: `isAfk()` triggers `leaveVoiceChannel` from within the RxJava chain

**File:** `CombinedAudioRecorderHandler.kt:141-166`

```kotlin
private fun isAfk(userCount: Int): Boolean {
    // ...
    if (isAfk) {
        thread {
            val save = pawa.autoSave(voiceChannel.guild.idLong)
            BotUtils.leaveVoiceChannel(voiceChannel, messageChannel, save)
        }
    }
    return isAfk
}
```

This is called from `doOnNext` in the RxJava chain. It spawns a new thread that calls
`BotUtils.leaveVoiceChannel()`, which calls `recorder.saveRecording()` and
`recorder.disconnect()`. These methods call `subject.onComplete()` while the chain is still
processing items (the `doOnNext` hasn't returned yet to the chain).

**Risk:** The AFK thread calls `saveRecording()` which sets `canReceive = false` and calls
`subject.onComplete()`. If the RxJava chain has buffered items still being processed, there
could be items emitted to `collectInto` concurrently with the save subscription trying to
read the accumulated data.

The `thread { }` call creates an unbounded number of threads — if AFK fires repeatedly
before `leaveVoiceChannel` completes, multiple threads could race on the same recorder.

---

### BUG-7 [MEDIUM]: `Pawa._recordings` map is not thread-safe

**File:** `Pawa.kt:40`

```kotlin
private val _recordings: MutableMap<String, Long> = mutableMapOf()
```

Written by `startRecording()` and `stopRecording()` from event threads, slash command
handlers, and AFK-triggered threads. No synchronization. Could corrupt internal HashMap
state under concurrent modification.

**Fix:** Use `ConcurrentHashMap`.

---

### BUG-8 [LOW]: `require` with `throw` in message lambda (Record.kt:97-99)

```kotlin
require(pawa.isStandalone || args.isEmpty()) {
    throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
}
```

`require`'s lambda is meant to return a message string. Here it throws `InvalidCommand`
inside the lambda, which works (the thrown exception propagates before `require` can wrap it
in `IllegalArgumentException`) but is confusing and unconventional.

**Fix:** Replace with `if (!condition) throw InvalidCommand(...)`.

---

### BUG-9 [LOW]: Confusing `null ?:` pattern in Record.kt

**File:** `Record.kt:38-40, 101-109`

```kotlin
val voiceChannel: AudioChannel? = if (pawa.isStandalone && selectedChannel != null) {
    selectedChannel
} else null ?: event.member?.voiceState?.channel
```

The `else null ?: x` is equivalent to `else x`. It works correctly but is misleading — it
looks like there should be a fallthrough from the `if` branch to the `?:` operator, but
that's not how Kotlin parses it.

**Fix:** Use `else -> event.member?.voiceState?.channel` directly, or use a `when` expression.

---

### BUG-10 [LOW]: `disconnect()` queue file deletion logic

**File:** `CombinedAudioRecorderHandler.kt:449-459`

```kotlin
if (standalone || (mp3File.exists() && mp3File.length() <= 0)) {
    logger.warn { "Skip deleting queue file..." }
} else {
    Files.deleteIfExists(...)
}
```

Standalone keeps queue files intentionally (for recovery). The non-standalone branch skips
deletion when an empty MP3 exists but **deletes** when no MP3 exists at all. This seems
inverted — you'd want to keep the queue file for debugging when the MP3 conversion failed
(no MP3 file), not when it produced an empty file.

---

### BUG-11 [LOW]: `BotUtils.leaveVoiceChannel()` hard-casts to CARH

**File:** `BotUtils.kt:205`

```kotlin
val recorder = audioManager.receivingHandler as CombinedAudioRecorderHandler
```

If a user starts recording via `BetaRecord` (which creates a `BaseAudioRecorder` subclass)
and then uses `/stop` (which calls `BotUtils.leaveVoiceChannel()`), this cast throws
`ClassCastException`. The beta commands have their own save/disconnect flow in `BetaSave`,
but the standard stop/save commands don't handle this.

---

## Standalone vs Shared Branching Inventory

All `if (standalone)` branches in CARH and their equivalents in the beta classes:

| CARH Location | Behavior | Beta Equivalent |
|---|---|---|
| `isAfk()` L142: `if (standalone) return false` | No AFK detection | `StandaloneAudioRecorder.shouldProcessAudio()` always true |
| `doOnNext` L219: `!standalone && percentage >= 90` | No size warning | `StandaloneAudioRecorder.onAudioDataWritten()` no-op |
| `collectInto` L229: `while (!standalone && ...)` | No size eviction | `StandaloneAudioRecorder.handleSizeLimit()` no-op |
| `uploadRecording` L350: filename format | Human-readable name | `StandaloneAudioRecorder.uploadRecording()` |
| `uploadRecording` L360: always upload to datastore | Always uploads | `StandaloneAudioRecorder.uploadRecording()` |
| `uploadRecording` L361: recording key format | Guild-prefixed path | `StandaloneAudioRecorder.uploadRecording()` |
| `uploadRecording` L379: raw URL display | Direct URL | `StandaloneAudioRecorder.uploadRecording()` |
| `uploadRecording` L387: no 24hr notice | No expiry message | `StandaloneAudioRecorder.uploadRecording()` |
| `disconnect` L450: keep queue files | Keep for recovery | Not yet handled in beta |

The beta classes correctly split all these behaviors. The remaining work is:
1. Making the beta path the **default** (not behind BetaRecord/BetaSave)
2. Updating `BotUtils.leaveVoiceChannel()` to work with `BaseAudioRecorder`
3. Handling queue file cleanup in the beta `disconnect()`
4. Removing CARH entirely

---

## The Lockup Problem

### Reproduction scenario

1. Bot is in production, recording in guild A
2. User in guild A types `/stop` or `/save`
3. JDA event dispatch thread (or coroutine dispatcher) calls `BotUtils.leaveVoiceChannel()`
4. `recorder.saveRecording()` is called:
   - Sets `canReceive = false`
   - Acquires semaphore (permits = 0)
   - Subscribes to `single` — callback will run on `Schedulers.io()`
   - Calls `subject.onComplete()`
   - **Blocks on `recordingLock.acquire()`** — waiting for the RxJava callback
5. RxJava callback runs on IO thread:
   - `queueFileIntoMp3()` — CPU-bound, usually fast
   - `uploadRecording()`:
     - `BotUtils.uploadFile()` → `messageChannel.sendFiles().complete()` — **BLOCKING HTTP**
     - `datastore.upload()` → S3 put with Failsafe retries — **BLOCKING HTTP with backoff**
   - If either blocks for extended time, the semaphore is held
6. The JDA event thread is blocked at step 4, unable to process any more events
7. If guild B tries to use any command → no response
8. If the upload eventually times out or fails with an exception that bypasses `finally` →
   **permanent deadlock**

### Why it doesn't reproduce in development

- Local S3 (Minio) responds in milliseconds
- Only 1-2 guilds active, no rate limiting
- Small recordings upload instantly to Discord
- No network latency or congestion

### Why it happens in production

- Discord rate limits during peak hours
- Large recordings (up to 256MB) take time to upload to S3
- S3 uploads retry with exponential backoff (2-30s)
- Multiple guilds stopping recordings simultaneously
- AFK-triggered stops can overlap with user-triggered stops

### Additional lockup vector: error in RxJava chain

If the RxJava chain errors (e.g., `IOException` writing to QueueFile, or encoder error),
the `Single` emits an error. The `saveRecording` subscriber receives `(null, throwable)`.
The callback tries to dereference `null` queueFile → NPE → `finally` block runs →
`queueFile.close()` throws NPE again → `recordingLock.release()` is in the same `finally`
block. If the NPE in `queueFile.close()` propagates, the lock is never released.

Wait — looking more carefully at the code:

```kotlin
single.subscribe { queueFile, _ ->
    // queueFile could be null on error, _ is the error
    val recordingFile = queueFile.let { ... }  // NPE if null — OUTSIDE try/catch/finally
    try {
        ...
    } catch (e: IOException) {
        ...
    } finally {
        queueFile.close()          // NPE if null
        recordingLock.release(1)   // never reached
    }
}
```

The `queueFile.let { ... }` line is **before** the try block. If `queueFile` is null, NPE
is thrown before entering the try block, and `recordingLock.release(1)` in the `finally`
block is never executed because we never entered the try block. **Permanent deadlock.**

---

## Refactoring Plan

### Phase 1: Promote `BaseAudioRecorder` to production (eliminate CARH)

The beta recorder hierarchy is already well-designed. The main work is integrating it with
the existing command infrastructure.

#### Step 1.1: Generalize `BotUtils.leaveVoiceChannel()`

Change the hard cast from `CombinedAudioRecorderHandler` to `AudioReceiveHandler`, then
use a `when` or interface to handle both types during migration:

```kotlin
fun leaveVoiceChannel(voiceChannel: AudioChannel, messageChannel: MessageChannel, save: Boolean) {
    val audioManager = guild.audioManager as AudioManagerImpl
    val handler = audioManager.receivingHandler

    audioManager.closeAudioConnection(ConnectionStatus.NOT_CONNECTED)
    recordingStatus(guild.selfMember, false)

    when (handler) {
        is BaseAudioRecorder -> {
            if (save) {
                val dest = defaultTextChannel(guild) ?: messageChannel
                sendMessage(dest, ":floppy_disk: ...")
                val (recording, lock) = handler.saveRecording(voiceChannel, dest)
                handler.disconnect(lock)
            } else {
                handler.disconnect()
            }
        }
        is CombinedAudioRecorderHandler -> {
            // legacy path — keep until fully migrated
            val (recording, lock) = if (save) { ... } else Pair(null, null)
            handler.disconnect(!save, recording, lock)
        }
    }
}
```

#### Step 1.2: Update `BotUtils.recordVoiceChannel()` to create the correct recorder

```kotlin
fun recordVoiceChannel(channel: AudioChannel, messageChannel: MessageChannel): BaseAudioRecorder {
    // ... existing permission checks ...
    val pawa: Pawa = getKoin().get()
    val recorder = if (pawa.isStandalone)
        StandaloneAudioRecorder(volume, channel, messageChannel)
    else
        SharedAudioRecorder(volume, channel, messageChannel)

    audioManager.receivingHandler = recorder
    return recorder
}
```

#### Step 1.3: Update `Record.handler()` return type

Change to return `BaseAudioRecorder` (or the common interface) instead of `CombinedAudioRecorderHandler`.

#### Step 1.4: Merge BetaRecord/BetaSave into Record/Save/Stop

Move the beta command logic into the standard commands, remove the Beta commands.

#### Step 1.5: Delete `CombinedAudioRecorderHandler` and `RecordingDisposable`

Once all paths use `BaseAudioRecorder`, remove the old classes entirely.

### Phase 2: Fix remaining issues in `BaseAudioRecorder`

#### Step 2.1: Thread-safe `speakers`

```kotlin
// In Recording entity
var speakers: MutableSet<User> = ConcurrentHashMap.newKeySet()
```

Or snapshot before reading:
```kotlin
val speakerSnapshot = synchronized(recording.speakers) { recording.speakers.toSet() }
```

#### Step 2.2: Thread-safe `silencedUsers`

```kotlin
protected val silencedUsers: MutableSet<Long> = ConcurrentHashMap.newKeySet()
```

#### Step 2.3: Thread-safe `Pawa._recordings`

```kotlin
private val _recordings: MutableMap<String, Long> = ConcurrentHashMap()
```

#### Step 2.4: Make uploads non-blocking

Change `BotUtils.uploadFile()` from `.complete()` (blocking) to `.submit()` (returns Future)
or `.queue()` (callback). The save flow should not block the calling thread on Discord HTTP.

#### Step 2.5: Queue file cleanup in `BaseAudioRecorder.disconnect()`

The current beta `disconnect()` closes the queue file but doesn't delete it. Port the
deletion logic from CARH (standalone keeps files, shared deletes them).

#### Step 2.6: Fix `BaseAudioRecorder.disconnect()` double-release

```kotlin
fun disconnect(recordingLock: Semaphore? = null) {
    isRecording.set(false)
    recordingLock?.let { lock ->
        try {
            lock.acquire()   // wait for save to complete
            lock.release()   // release the one we just acquired
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
    cleanup()
    recordingLock?.release()  // BUG: releases an extra permit
}
```

The `recordingLock?.release()` after `cleanup()` releases a permit that was never acquired,
inflating the semaphore count. This should be removed — the `lock.release()` inside the
`let` block already balances the `lock.acquire()`.

### Phase 3: Improve testability

See [Testability Plan](#testability-plan) below.

---

## Testability Plan

### Current state: Zero tests for recording handlers

No unit or integration tests exist for `CombinedAudioRecorderHandler`, `BaseAudioRecorder`,
`StandaloneAudioRecorder`, or `SharedAudioRecorder`. This is the most critical gap —
the lockup bug cannot be reliably reproduced or verified fixed without tests.

### What makes CARH untestable

1. **Tight coupling to JDA:** Directly uses `AudioChannel`, `MessageChannel`, `Guild`,
   `AudioManager` — all JDA types that are difficult to mock
2. **Koin DI in constructor:** `by inject()`, `getKoin().getProperty()` — requires a running
   Koin container
3. **Database transactions in constructor:** `createRecording()` does a DB transaction during
   object initialization
4. **Side effects in constructor:** Creates ULID, DB record, RxJava subscription, LAME encoder
   — all before any method is called
5. **Static calls to `BotUtils`:** `BotUtils.sendMessage()`, `BotUtils.uploadFile()`,
   `BotUtils.leaveVoiceChannel()` are all static methods

### How to make `BaseAudioRecorder` testable

#### Step 3.1: Extract interfaces for external dependencies

```kotlin
interface AudioUploader {
    fun uploadToDiscord(channel: MessageChannel, file: File, filename: String): Message?
    fun sendMessage(channel: MessageChannel, message: String)
}

interface RecordingStore {
    fun createRecording(ulid: String, channelId: Long, channelName: String, guildId: Long): Recording?
    fun updateRecording(recording: Recording, size: Long, url: String, duration: Duration)
    fun deleteRecording(recording: Recording)
}
```

#### Step 3.2: Constructor injection instead of Koin `inject()`

```kotlin
class BaseAudioRecorder(
    val volume: Double,
    val voiceChannel: AudioChannel,    // could be further abstracted
    val messageChannel: MessageChannel,
    val datastore: Datastore,
    val recordingStore: RecordingStore,
    val uploader: AudioUploader,
    val config: RecorderConfig          // fileFormat, vbr, dataDirectory, etc.
)
```

#### Step 3.3: Create test doubles

```kotlin
class FakeAudioChannel(val id: Long, val name: String, val guildId: Long) { ... }
class FakeMessageChannel(val id: Long, val name: String) {
    val sentMessages = mutableListOf<String>()
}
class InMemoryDatastore : Datastore { ... }
class InMemoryRecordingStore : RecordingStore { ... }
```

#### Step 3.4: Write the critical tests

**Test 1: Basic recording lifecycle**
```
Given: A BaseAudioRecorder is created
When:  100 audio frames are received via handleCombinedAudio()
And:   saveRecording() is called
Then:  An MP3 file is produced
And:   The recording is uploaded
And:   The semaphore is released within 5 seconds
```

**Test 2: Concurrent stop while recording**
```
Given: Audio is being received at 50 frames/second (simulating real-time)
When:  saveRecording() is called from a different thread
Then:  All queued audio is processed
And:   The recording is saved without deadlock
And:   The test completes within 30 seconds
```

**Test 3: AFK detection (SharedAudioRecorder)**
```
Given: A SharedAudioRecorder is recording
When:  6000 silent frames (2 minutes) are received
Then:  leaveVoiceChannel is called
```

**Test 4: Size limit enforcement (SharedAudioRecorder)**
```
Given: A SharedAudioRecorder with 256MB limit
When:  Audio data exceeds the limit
Then:  Oldest data is evicted
And:   Recording size stays under limit
```

**Test 5: Concurrent save + AFK race condition**
```
Given: A SharedAudioRecorder that has been AFK for 1m59s
When:  A user triggers /stop at the same moment AFK fires
Then:  Only one save/disconnect occurs
And:   No deadlock or double-close
```

**Test 6: Load test — simulate production conditions**
```
Given: 50 concurrent recording sessions
When:  All 50 stop simultaneously
And:   Upload latency is simulated at 2-5 seconds each
Then:  All 50 complete within 60 seconds
And:   No thread pool exhaustion
```

#### Step 3.5: Stress test harness

Create a test harness that can:
1. Spin up N simulated recordings
2. Feed audio frames at realistic rates
3. Simulate slow uploads (configurable latency)
4. Trigger saves concurrently
5. Assert no deadlocks (timeout-based)
6. Measure thread pool utilization

This would be implemented as a JUnit test with `@Timeout` annotations and
`CountDownLatch` for synchronization, using the extracted interfaces with
configurable delays.

---

## Priority Order

| Priority | Task | Effort | Risk reduction |
|----------|------|--------|----------------|
| 1 | Fix the lockup: eliminate RxJava path by promoting BaseAudioRecorder | Large | **Critical** |
| 2 | Fix thread-safety bugs (speakers, silencedUsers, _recordings) | Small | High |
| 3 | Make uploads non-blocking | Medium | High |
| 4 | Fix BaseAudioRecorder.disconnect() double-release | Small | Medium |
| 5 | Add queue file cleanup to BaseAudioRecorder | Small | Low |
| 6 | Extract interfaces for testability | Medium | Enables testing |
| 7 | Write critical lifecycle tests | Medium | Prevents regressions |
| 8 | Write load/stress tests | Large | Catches production issues |
| 9 | Delete CARH and beta commands | Small | Maintenance |
