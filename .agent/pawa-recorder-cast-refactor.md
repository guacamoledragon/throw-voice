# Pawa Recorder Cast Refactor

## Goal

Eliminate all `audioManager.receivingHandler as AudioRecorder` casts by having
`Pawa` own the active recorder lifecycle. `Pawa` must remain **JDA-free** and
testable without a live Discord connection.

## Design Principle

`Pawa` holds *actions the bot can do* — start recording, stop recording, silence
a user, update volume — but never directly references JDA types like
`AudioChannel`, `MessageChannel`, `AudioManager`, or `AudioReceiveHandler`.

The JDA-specific wiring (connecting to voice, setting `receivingHandler`, closing
connections, setting nickname) stays in `BotUtils`.

---

## Current State (as of this branch)

### Where JDA types leak through Pawa today

`Pawa` is already JDA-free except for:
- `Recording.speakers: MutableSet<net.dv8tion.jda.api.entities.User>` — a transient
  (non-persisted) field on the `Recording` entity. This is a JDA type that lives in
  a database entity. Fixing this is out of scope here but worth noting.

### Where casts happen today

11 call sites cast `audioManager.receivingHandler` to `AudioRecorder` (or previously
`CombinedAudioRecorderHandler`). These are the sources of spaghetti — every command
reaches into JDA's audio manager to fish out the recorder, instead of asking Pawa.

| # | File:Line | Cast | What's accessed | How Pawa could provide it |
|---|-----------|------|-----------------|---------------------------|
| 1 | `BotUtils.kt:210` | `as AudioRecorder` | `saveRecording()`, `disconnect()` | `pawa.saveAndDisconnect(guildId)` |
| 2 | `BotUtils.kt:553` | `as? AudioRecorder` | `volume` (setter) | `pawa.updateVolume(guildId, v)` |
| 3 | `BotUtils.kt:384` | `as AudioReceiveHandler` | N/A (creation/assignment) | `pawa.registerRecorder(guildId, recorder)` |
| 4 | `Save.kt:61` | `as? AudioRecorder` + `!!` | `recording` (post-save) | return from `leaveVoiceChannel` or `pawa.lastRecording(guildId)` |
| 5 | `Stop.kt:37` | implicit (return) | `session` | `pawa.stopRecordingForGuild(guildId)` |
| 6 | `Record.kt:67` | implicit (return) | `session` | internalize `startRecording` in factory, return session string |
| 7 | `Ignore.kt:44` | `as? AudioRecorder` + `!!` | `silenceUser()`, `session` | `pawa.silenceUser(guildId, userId)` |
| 8 | `Test.kt:34` | `as? AudioRecorder` + `!!` | `recording` (post-save) | same as #4 |
| 9 | `BetaSave.kt:27` | `as AudioRecorder` | `saveRecording()`, `disconnect()` | same as #1. **Bug: never calls `stopRecording`** |
| 10 | `BetaRecord.kt:26` | implicit (return) | `session` | same as #6 |
| 11 | `BetaIgnore.kt:28` | `as AudioRecorder` | `session`, `silenceUser()` | same as #7 |

---

## Proposed Design

### New state on Pawa

```kotlin
// Pawa.kt (additions)

// guildId -> live recorder. ConcurrentHashMap for thread safety.
private val _activeRecorders: ConcurrentHashMap<Long, AudioRecorder> = ConcurrentHashMap()

// guildId -> Recording entity produced by the last save.
// Kept briefly so the Save/Stop slash handler can retrieve it for the reply embed.
private val _lastRecordings: ConcurrentHashMap<Long, Recording> = ConcurrentHashMap()
```

Note: `AudioRecorder` is our own interface (`tech.gdragon.listener.AudioRecorder`),
NOT a JDA type. It only exposes: `session`, `recording`, `volume`, `saveRecording()`,
`disconnect()`, `silenceUser()`. Pawa depends on this interface, not on JDA.

### New methods on Pawa

```kotlin
/**
 * Register a newly-created recorder for [guildId].
 * Called by BotUtils.recordVoiceChannel after the recorder is created and
 * assigned to JDA's AudioManager.
 *
 * Returns the session ID.
 */
fun registerRecorder(guildId: Long, recorder: AudioRecorder): String {
    _activeRecorders[guildId] = recorder
    _recordings[recorder.session] = guildId
    return recorder.session
}

/**
 * Look up the session ID for an active recording in [guildId].
 */
fun sessionForGuild(guildId: Long): String? {
    return _activeRecorders[guildId]?.session
}

/**
 * Stop the recording for [guildId], save if [save] is true.
 *
 * The save/disconnect operations are delegated to the recorder.
 * [voiceChannelId], [messageChannelId], and [saveAction] are callbacks
 * provided by the caller (BotUtils) to handle JDA-specific work like
 * resolving channels. This keeps JDA types out of Pawa.
 *
 * Returns the Recording entity if saved, or null.
 */
fun stopRecording(
    guildId: Long,
    save: Boolean,
    saveAction: ((AudioRecorder) -> Pair<Recording?, Semaphore?>)? = null
): Recording? {
    val recorder = _activeRecorders.remove(guildId) ?: return null
    _recordings -= recorder.session

    val (recording, lock) = if (save && saveAction != null) {
        saveAction(recorder)
    } else {
        Pair(null, null)
    }

    recorder.disconnect(save, recording, lock)

    recording?.let { _lastRecordings[guildId] = it }
    return recording
}

/**
 * Retrieve the Recording entity from the last save for [guildId].
 * Consumed once — returns null on subsequent calls.
 */
fun consumeLastRecording(guildId: Long): Recording? {
    return _lastRecordings.remove(guildId)
}

/**
 * Silence a user in the active recording for [guildId].
 */
fun silenceUser(guildId: Long, userId: Long) {
    _activeRecorders[guildId]?.let { recorder ->
        recorder.silenceUser(userId)
        _ignoredUsers[recorder.session] = _ignoredUsers.getOrDefault(recorder.session, emptyList()) + userId
    }
}

/**
 * Update the recording volume for [guildId].
 * Updates both the live recorder and the persisted DB setting.
 */
fun updateVolume(guildId: Long, volumePercent: Double): Double {
    val actualVolume = volumePercent.coerceIn(0.0..1.0)
    _activeRecorders[guildId]?.volume = actualVolume
    // Also persist to DB (existing logic from current volume() method)
    return transaction {
        Settings.find { Tables.Settings.guild eq guildId }
            .firstOrNull()
            ?.let { it.volume = BigDecimal.valueOf(actualVolume); actualVolume }
            ?: 0.0
    }
}
```

### Alternative: Keep saveAction out of Pawa

If you don't want Pawa to know about `saveRecording()` / `disconnect()` at all (they
involve I/O, file writing, uploads), a simpler split:

```kotlin
// Pawa only manages the map
fun deregisterRecorder(guildId: Long): AudioRecorder? {
    val recorder = _activeRecorders.remove(guildId)
    recorder?.let { _recordings -= it.session }
    return recorder
}
```

Then `BotUtils.leaveVoiceChannel` does:
```kotlin
val recorder = pawa.deregisterRecorder(guildId)
    ?: error("No active recorder for guild $guildId")
// JDA cleanup
audioManager.closeAudioConnection()
recordingStatus(member, false)
// Recorder ops
val (recording, lock) = if (save) recorder.saveRecording(...) else Pair(null, null)
recorder.disconnect(save, recording, lock)
pawa.storeLastRecording(guildId, recording)
```

This keeps Pawa completely ignorant of save/disconnect mechanics. BotUtils is the
orchestrator. Pawa is just the registry.

---

## Migration Plan (file by file)

### Step 1: Add state and methods to Pawa

File: `src/main/kotlin/tech/gdragon/api/pawa/Pawa.kt`

1. Add `_activeRecorders: ConcurrentHashMap<Long, AudioRecorder>` field
2. Change `_recordings` from `MutableMap` to `ConcurrentHashMap` (thread safety)
3. Add `registerRecorder(guildId, recorder): String`
4. Add `deregisterRecorder(guildId): AudioRecorder?` (or `stopRecording` with callback)
5. Add `sessionForGuild(guildId): String?`
6. Add `silenceUser(guildId, userId)`
7. Modify `updateVolume` to also hot-update the live recorder
8. Add `storeLastRecording(guildId, recording)` and `consumeLastRecording(guildId)`
9. Change `startRecording(session, guildId)` to call `registerRecorder` internally
   (or deprecate in favor of `registerRecorder`)
10. Change `stopRecording(session)` to also remove from `_activeRecorders`

**Import note**: `Pawa` will import `tech.gdragon.listener.AudioRecorder`. This is
our own interface, NOT a JDA type. `AudioRecorder` itself references JDA types in its
method signatures (`AudioChannel`, `MessageChannel`). To keep Pawa fully JDA-free,
we'd need to further abstract `saveRecording` to not take JDA types — but that's a
deeper refactor. For now, the `deregisterRecorder` approach avoids this: Pawa never
calls `saveRecording()` directly, so it only sees `AudioRecorder.session`,
`AudioRecorder.recording`, `AudioRecorder.volume`, and `AudioRecorder.silenceUser()` —
none of which involve JDA types.

**If going fully JDA-free**: Define a minimal `RecorderHandle` interface that Pawa
uses, with only the JDA-free subset:

```kotlin
// In the pawa API package, no JDA imports
interface RecorderHandle {
    val session: String
    val recording: Recording?
    var volume: Double
    fun silenceUser(userId: Long)
}
```

Then `AudioRecorder` extends `RecorderHandle` and adds the JDA-specific methods.
Pawa holds `Map<Long, RecorderHandle>`.

### Step 2: Update `BotUtils.recordVoiceChannel`

File: `src/main/kotlin/tech/gdragon/BotUtils.kt`

Current:
```kotlin
val recorder: AudioRecorder = when (pawa.recorderImpl) { ... }
audioManager.receivingHandler = recorder as AudioReceiveHandler
recordingStatus(channel.guild.selfMember, true)
return recorder
```

Proposed:
```kotlin
val recorder: AudioRecorder = when (pawa.recorderImpl) { ... }
audioManager.receivingHandler = recorder as AudioReceiveHandler
recordingStatus(channel.guild.selfMember, true)
val session = pawa.registerRecorder(channel.guild.idLong, recorder)
return session  // return String, not AudioRecorder
```

Return type changes from `AudioRecorder` to `String` (session ID).

### Step 3: Update `BotUtils.leaveVoiceChannel`

File: `src/main/kotlin/tech/gdragon/BotUtils.kt`

Current:
```kotlin
val recorder = audioManager.receivingHandler as AudioRecorder  // CAST
audioManager.closeAudioConnection()
val (recording, lock) = if (save) recorder.saveRecording(...) else Pair(null, null)
recorder.disconnect(save, recording, lock)
return recorder
```

Proposed:
```kotlin
val recorder = pawa.deregisterRecorder(guild.idLong)
    ?: error("No active recorder for guild ${guild.idLong}")
audioManager.closeAudioConnection()
recordingStatus(guild.selfMember, false)
val (recording, lock) = if (save) {
    val dest = defaultTextChannel(guild) ?: messageChannel
    sendMessage(dest, ":floppy_disk: ...")
    recorder.saveRecording(voiceChannel, dest)
} else Pair(null, null)
recorder.disconnect(save, recording, lock)
recording?.let { pawa.storeLastRecording(guild.idLong, it) }
return recorder.session  // return String, not AudioRecorder
```

Return type changes from `AudioRecorder` to `String` (session ID).

**Note**: `leaveVoiceChannel` needs `pawa` as a parameter. Currently it doesn't have
one — it uses `getKoin().get()` or could take it as a parameter. Adding `pawa: Pawa`
as a parameter is cleaner and more testable. All callers already have a `pawa` reference.

### Step 4: Update `BotUtils.updateVolume`

Current:
```kotlin
fun updateVolume(guild: DiscordGuild, volume: Double) {
    val handler = guild.audioManager.receivingHandler as? AudioRecorder
    handler?.volume = volume
}
```

Proposed:
```kotlin
fun updateVolume(pawa: Pawa, guildId: Long, volume: Double) {
    pawa.updateVolume(guildId, volume)
}
```

Or callers can call `pawa.updateVolume(guildId, volume)` directly and this method
can be deleted.

### Step 5: Update `Record.kt` and `BetaRecord.kt`

Current:
```kotlin
val recorder = BotUtils.recordVoiceChannel(voiceChannel, messageChannel)
pawa.startRecording(recorder.session, guild.idLong)
RecordingStartedReply(voiceChannel.id, recorder.session, lang).message
```

Proposed (after `recordVoiceChannel` returns session string):
```kotlin
val session = BotUtils.recordVoiceChannel(voiceChannel, messageChannel)
// pawa.startRecording is now internal to recordVoiceChannel
RecordingStartedReply(voiceChannel.id, session, lang).message
```

### Step 6: Update `Stop.kt`

Current:
```kotlin
val recorder = BotUtils.leaveVoiceChannel(audioChannel, guildChannel, save)
pawa.stopRecording(recorder.session)
```

Proposed (after `leaveVoiceChannel` returns session string and handles stopRecording internally):
```kotlin
BotUtils.leaveVoiceChannel(pawa, audioChannel, guildChannel, save)
// stopRecording is now internal to leaveVoiceChannel via deregisterRecorder
```

### Step 7: Update `Save.kt`

Current (`slashHandler`):
```kotlin
val message = handler(pawa, it, messageChannel)
if (message != null) {
    BotUtils.reply(event, MessageCreate(message))
} else {
    val recorder = event.guild?.audioManager?.receivingHandler as? AudioRecorder
    val recording = recorder?.recording!!  // FRAGILE: handler may have nulled receivingHandler
    val recordingEmbed = RecordingReply(recording, pawa.config.appUrl)
    BotUtils.reply(event, recordingEmbed.message)
}
```

Proposed:
```kotlin
val message = handler(pawa, it, messageChannel)
if (message != null) {
    BotUtils.reply(event, MessageCreate(message))
} else {
    val recording = pawa.consumeLastRecording(it.idLong)
    if (recording != null) {
        val recordingEmbed = RecordingReply(recording, pawa.config.appUrl)
        BotUtils.reply(event, recordingEmbed.message)
    }
}
```

No cast. No race condition. `Pawa` holds the recording from the save.

### Step 8: Update `Ignore.kt` and `BetaIgnore.kt`

Current:
```kotlin
(guild.audioManager.receivingHandler as? AudioRecorder)!!.let { handler ->
    ignoredUserIds.forEach(handler::silenceUser)
    pawa.ignoreUsers(handler.session, ignoredUserIds)
}
```

Proposed:
```kotlin
ignoredUserIds.forEach { pawa.silenceUser(guild.idLong, it) }
// pawa.silenceUser internally calls recorder.silenceUser AND pawa.ignoreUsers
```

### Step 9: Update `Test.kt`

Current:
```kotlin
Save.handler(pawa, event.guild, event.channel)
val recorder = event.guild.audioManager.receivingHandler as? AudioRecorder
val recording = recorder?.recording!!
```

Proposed:
```kotlin
Save.handler(pawa, event.guild, event.channel)
val recording = pawa.consumeLastRecording(event.guild.idLong)!!
```

### Step 10: Update `BetaSave.kt`

Current:
```kotlin
val recorder = audioManager.receivingHandler as AudioRecorder
audioManager.closeAudioConnection()
val (recording, lock) = recorder.saveRecording(voiceChannel, messageChannel)
recorder.disconnect(save = true, recording, lock)
// BUG: never calls pawa.stopRecording
```

Proposed:
```kotlin
BotUtils.leaveVoiceChannel(pawa, voiceChannel, messageChannel, save = true)
val recording = pawa.consumeLastRecording(event.guild!!.idLong)
// No cast. No manual disconnect. No forgotten stopRecording.
```

### Step 11: Update `autoRecord` and `autoStop` in BotUtils

These already delegate to `recordVoiceChannel` and `leaveVoiceChannel`. Once those
methods are updated, these should work automatically. Just pass `pawa` through.

### Step 12: Update EventListener

`EventListener` calls `BotUtils.autoRecord(pawa, guild, channel)` and
`BotUtils.autoStop(guild, channel, save)`. After the refactor:

- `autoRecord` already has `pawa`
- `autoStop` needs `pawa` added as a parameter (currently it doesn't have it —
  it's called from `onGuildVoiceLeave` and `onGuildVoiceMove` which both have `pawa`)

---

## Verification Checklist

After the refactor, verify:

- [ ] Zero remaining `as.*AudioRecorder` or `as.*CombinedAudioRecorderHandler` casts
      outside of `BotUtils.kt` (the one place that bridges JDA and Pawa)
- [ ] `Pawa` has zero JDA imports (or at most `RecorderHandle` which is JDA-free)
- [ ] `BotUtils.leaveVoiceChannel` is the ONLY place that calls `saveRecording()` +
      `disconnect()` on a recorder
- [ ] `BotUtils.recordVoiceChannel` is the ONLY place that creates recorders
- [ ] `pawa.stopRecording` is always called (no leaking sessions in `_recordings`)
- [ ] `Save.slashHandler` no longer races with `closeAudioConnection` for the
      `Recording` entity
- [ ] Existing tests still pass: `CombinedAudioRecorderHandlerTest`,
      `SharedAudioRecorderTest`, `PawaTest`, `BotUtilsTest`
- [ ] New unit tests for `Pawa.registerRecorder`, `deregisterRecorder`,
      `silenceUser`, `updateVolume`, `consumeLastRecording` (all testable without JDA)

---

## Files Changed (estimated)

| File | Change |
|------|--------|
| `Pawa.kt` | Add `_activeRecorders`, `_lastRecordings`, new methods |
| `BotUtils.kt` | Change `recordVoiceChannel` and `leaveVoiceChannel` signatures, remove `updateVolume` cast, pass `pawa` where needed |
| `Record.kt` | Use session string from `recordVoiceChannel`, remove `pawa.startRecording` call |
| `Stop.kt` | Remove `pawa.stopRecording(recorder.session)`, handled internally |
| `Save.kt` | Use `pawa.consumeLastRecording(guildId)` instead of cast |
| `Ignore.kt` | Use `pawa.silenceUser(guildId, userId)` |
| `Test.kt` | Use `pawa.consumeLastRecording(guildId)` |
| `BetaRecord.kt` | Same as Record.kt |
| `BetaSave.kt` | Delegate to `leaveVoiceChannel`, use `consumeLastRecording` |
| `BetaIgnore.kt` | Same as Ignore.kt |
| `EventListener.kt` | Pass `pawa` to `autoStop` |
| `AudioRecorder.kt` | Optionally split into `RecorderHandle` (JDA-free) + `AudioRecorder` (JDA) |
| `Volume.kt` (if exists) | Call `pawa.updateVolume` instead of `BotUtils.updateVolume` |
