# Upload Datastore Fallback + Recovery Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When the Discord attachment upload of a finished recording fails, deliver the recording via the datastore (S3/Minio) instead of stranding it on disk; and stop manual recovery from leaving regenerated `.mp3` files behind.

**Architecture:** Two independent, small changes to existing methods. (1) `SharedAudioRecorder.uploadRecording` currently aborts entirely when the Discord attachment upload throws — restructure it so an attachment failure (exception or null) routes into the already-existing datastore branch. (2) `Pawa.uploadRecording` (the recovery path) uploads the regenerated `.mp3` to the datastore but never deletes the local file — delete it after a successful upload.

**Tech Stack:** Kotlin, JDA, Exposed ORM, Koin, kotest (FunSpec) + mockk, Maven.

**Why (evidence from prod, week of 2026-06-29):** All 8 genuinely un-delivered recordings had an `Error uploading recording` ERROR from the Discord attachment upload — 5× `400001: Access to file uploads has been limited for this guild`, 3× permission errors (`MESSAGE_SEND`, `MESSAGE_ATTACH_FILES`, `VIEW_CHANNEL`); Honeycomb additionally shows 6× `10003: Unknown Channel` in the same window. None were crash-induced. In every case the recording was fully generated on disk and the datastore was healthy — a fallback upload would have delivered all of them with zero `/recover` requests. Separately, 3 of 11 on-disk `.mp3` were residue of successful manual recoveries. See `.agent/runbooks/stability-runbook.md` (2026-07-06 baseline).

## Global Constraints

- Do NOT touch prod (`pawa.im`). All work is local code + tests.
- Kotlin code style: match surrounding code; project uses ktfmt conventions already in the files.
- Tests: kotest FunSpec with mockk, run via Maven surefire. `PawaTest` needs Docker (testcontainers Postgres); `SharedAudioRecorderTest` does not.
- Out of scope (do not do): `.queue` file disk-leak cleanup, background auto-recovery worker, changing the 60s bounded-wait in `disconnect`, changing `Recover.kt` owner-gating.
- Commit per task, messages in imperative mood, no step labels in titles.

---

### Task 1: Datastore fallback in `SharedAudioRecorder.uploadRecording`

**Files:**
- Modify: `src/main/kotlin/tech/gdragon/listener/SharedAudioRecorder.kt:94-157` (the `uploadRecording` override)
- Test: `src/test/kotlin/tech/gdragon/listener/SharedAudioRecorderTest.kt`

**Interfaces:**
- Consumes: `BaseAudioRecorder.uploadAttachment(messageChannel, recordingFile, filename): Message?` (returns null when file ≥ `Message.MAX_FILE_SIZE`, otherwise calls `BotUtils.uploadFile` which throws JDA exceptions on Discord-side failure), `datastore.upload(key, file): UploadResult` (fields: `key`, `timestamp`, `size`, `url`), `recordingRecord: Recording?` (Exposed DAO, protected field on `BaseAudioRecorder`).
- Produces: no signature changes. Behavior change only: attachment failure → datastore upload → DB `url` set to `result.url` → local file deleted.

**Current behavior (the bug):** `uploadAttachment` is called at the top of a single `try` block. When it throws (e.g. JDA `ErrorResponseException` 400001, `InsufficientPermissionException`), control jumps straight to the outer `catch` — the datastore branch, DB update, user notification, and local-file cleanup are all skipped. The `.mp3` stays on disk with no `url` in the DB, requiring manual `/recover`.

- [ ] **Step 1: Write the failing test**

Add to the bottom of the test list in `src/test/kotlin/tech/gdragon/listener/SharedAudioRecorderTest.kt` (inside the `FunSpec({ ... })` body, alongside the existing `test(...)` blocks). Add these imports to the file's import list: `io.kotest.matchers.shouldBe` and `tech.gdragon.db.dao.Recording`.

```kotlin
  test("falls back to datastore when Discord attachment upload throws").config(
    timeout = kotlin.time.Duration.parse("15s")
  ) {
    // Arrange: Discord rejects the attachment upload (e.g. 400001 guild upload limit)
    every { BotUtils.uploadFile(any(), any(), any()) } throws
      RuntimeException("400001: Access to file uploads has been limited for this guild")

    val recorder = SharedAudioRecorder(1.0, mockVoiceChannel, mockMessageChannel)
    feedAudioFrames(recorder, 30)

    val (recording, lock) = recorder.saveRecording(mockVoiceChannel, mockMessageChannel)
    recorder.disconnect(lock)

    // Assert: recording was delivered via the datastore instead
    verify { mockDatastore.upload(any(), any()) }
    transaction {
      Recording.findById(recording!!.id.value)!!.url shouldBe "http://localhost/rec.mp3"
    }
    // Local file was cleaned up (no leftover .mp3 requiring /recover)
    File(tempDir, "recordings/${recorder.session}.mp3").exists() shouldBe false

    // Restore the shared mock for subsequent tests
    every { BotUtils.uploadFile(any(), any(), any()) } returns null
  }
```

Note: `mockDatastore` is stubbed in `beforeSpec` to return `UploadResult("key", Instant.now(), 100L, "http://localhost/rec.mp3")` — the URL asserted above. `BotUtils` is already `mockkObject`'d in `beforeSpec`.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SharedAudioRecorderTest`
Expected: the new test FAILS — `mockDatastore.upload` is never called (the exception aborts `uploadRecording`), and/or the leftover `.mp3` exists. The pre-existing tests must still pass.

- [ ] **Step 3: Implement the fallback**

Replace the entire `uploadRecording` override in `src/main/kotlin/tech/gdragon/listener/SharedAudioRecorder.kt` (currently lines 94–157) with:

```kotlin
  override fun uploadRecording(recordingFile: File, voiceChannel: AudioChannel, messageChannel: MessageChannel) {
    try {
      val filename = recordingFile.name

      // Try Discord first for small files; on failure fall through to the datastore.
      val attachment = try {
        uploadAttachment(messageChannel, recordingFile, filename)?.attachments?.first()
      } catch (e: Exception) {
        logger.warn(e) { "Discord attachment upload failed, falling back to datastore: $session" }
        null
      }

      if (attachment != null && recordingFile.length() < DISCORD_MAX_RECORDING_SIZE) {
        // Discord-only upload
        transaction {
          recordingRecord?.apply {
            size = recordingFile.length()
            modifiedOn = now()
            url = attachment.proxyUrl
            duration = this@SharedAudioRecorder.duration
          }
        }

        val appUrl = getKoin().getProperty<String>("APP_URL")
        val recordingUrl = "$appUrl/v1/recordings?guild=${voiceChannel.guild.idLong}&session-id=$session"
        val message = """|:microphone2: **Recording for <#${voiceChannel.id}> has been uploaded!**
                                |$recordingUrl""".trimMargin()

        tech.gdragon.BotUtils.sendMessage(messageChannel, message)
      } else {
        // Large files, or Discord upload failed → upload to datastore
        val recordingKey = "${voiceChannel.guild.id}/$filename"
        val result = datastore.upload(recordingKey, recordingFile)

        transaction {
          recordingRecord?.apply {
            size = result.size
            modifiedOn = result.timestamp
            url = result.url
            duration = this@SharedAudioRecorder.duration
          }
        }

        val appUrl = getKoin().getProperty<String>("APP_URL")
        val recordingUrl = if (appUrl != null) {
          "$appUrl/v1/recordings?guild=${voiceChannel.guild.idLong}&session-id=$session"
        } else result.url

        val message = """|:microphone2: **Recording for <#${voiceChannel.id}> has been uploaded!**
                                |$recordingUrl
                                |
                                |_Recording will only be available for 24hrs_""".trimMargin()

        tech.gdragon.BotUtils.sendMessage(messageChannel, message)
      }

      // Cleanup local file
      if (recordingFile.delete()) {
        logger.info { "Successfully deleted local file ${recordingFile.name}" }
      } else {
        logger.warn { "Could not delete local file ${recordingFile.name}" }
      }
    } catch (e: Exception) {
      logger.error(e) { "Error uploading recording: $session" }
      val errorMessage =
        """|:no_entry_sign: _Error uploading recording, please visit support server and provide Session ID._
                                 |_Session ID: `$session`_""".trimMargin()
      tech.gdragon.BotUtils.sendMessage(messageChannel, errorMessage)
    }
  }
```

Preserve the existing imports; everything referenced (`now()`, `getKoin()`, `DISCORD_MAX_RECORDING_SIZE`, `transaction`) is already imported/available in the file. Keep the multi-line string indentation exactly as in the current file (the odd continuation indentation is pre-existing style).

Behavior notes the implementer should understand (no action needed):
- The old code set `url = attachment?.proxyUrl ?: "Discord Only"` when the attachment came back null; the new code routes null attachments to the datastore branch instead — that's the point of the change.
- If `datastore.upload` itself throws, the outer `catch` still produces the "visit support server" message and leaves the file for `/recover` — unchanged, and acceptable until the auto-recovery worker exists.
- `BotUtils.sendMessage` catches Discord errors internally (prod logs show `Error sending message - ... 10003: Unknown Channel` as non-fatal), so a dead notification channel does not prevent the DB update or the file cleanup.

- [ ] **Step 4: Run the full test class, verify all pass**

Run: `mvn test -Dtest=SharedAudioRecorderTest`
Expected: PASS, including all pre-existing tests. (Pre-existing tests stub `BotUtils.uploadFile` to return `null`, which now routes them through the datastore branch — `mockDatastore` is relaxed, and no existing assertion depends on the Discord-only branch, so they must still pass. If one fails, fix the test's stubbing, not the production code.)

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/tech/gdragon/listener/SharedAudioRecorder.kt src/test/kotlin/tech/gdragon/listener/SharedAudioRecorderTest.kt
git commit -m "Fall back to datastore upload when Discord attachment upload fails"
```

---

### Task 2: Delete regenerated `.mp3` after successful recovery upload

**Files:**
- Modify: `src/main/kotlin/tech/gdragon/api/pawa/Pawa.kt:201-233` (`Pawa.uploadRecording`)
- Test: `src/test/kotlin/tech/gdragon/api/pawa/PawaTest.kt` (inside the `pawaTests` funSpec, `context("when recover")` block)
- Modify: `.agent/runbooks/stability-runbook.md` (one bullet, see Step 5)

**Interfaces:**
- Consumes: `Pawa.uploadRecording(sessionId: String, mp3File: File, datastore: Datastore): Recording?` — called only from `Pawa.recoverRecording` (verified: no other callers). `datastore.upload` here is a real `LocalDatastore` in tests.
- Produces: no signature change. New behavior: `mp3File` is deleted iff the function returns non-null (upload succeeded and a `Recording` row was updated/created).

**Current behavior (the bug):** `recoverRecording` regenerates the `.mp3` from the `.queue` file (or finds a leftover one), uploads it to the datastore, sets the DB `url` — and leaves the local `.mp3` forever. These files are false positives in the "un-delivered recordings" ground-truth metric.

- [ ] **Step 1: Write the failing test**

Add inside `context("when recover")` in `src/test/kotlin/tech/gdragon/api/pawa/PawaTest.kt` (after the last existing test in that context). All needed imports (`Files`, `File`, `QueueFile`, `ULID`, `shouldBe`, `shouldNotBeNull`) are already present in the file.

```kotlin
    test("it should delete the regenerated mp3 file after a successful recovery upload") {
      val dataDirectory = pawa.config.dataDirectory
      val sessionId = ULID.random()

      // Create queue file (simulates a recording whose upload never happened)
      Files.createDirectories(File(dataDirectory, "recordings").toPath())
      QueueFile(File(dataDirectory, "recordings/$sessionId.queue"))

      transaction(db.database) {
        val guild = Guild.findOrCreate(guildId, "Test Guild")
        val channel = Channel.findOrCreate(1L, "fake-voice-channel", guildId)

        Recording.new(sessionId) {
          this.channel = channel
          this.guild = guild
        }
      }
      val result = pawa.recoverRecording(ds, sessionId)

      result.recording.shouldNotBeNull()
      // The regenerated mp3 must not linger on disk after the datastore upload
      File(dataDirectory, "recordings/$sessionId.mp3").exists() shouldBe false
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=PawaTest` (requires Docker for the Postgres testcontainer; both `H2:` and `Postgres:` variants of the new test run)
Expected: the new test FAILS on the `exists() shouldBe false` assertion — the mp3 currently remains on disk.

- [ ] **Step 3: Implement the cleanup**

In `src/main/kotlin/tech/gdragon/api/pawa/Pawa.kt`, replace the body of `uploadRecording` (currently lines 201–233) with:

```kotlin
  fun uploadRecording(sessionId: String, mp3File: File, datastore: Datastore): Recording? {
    val recording = transaction(db.database) {
      Recording.findById(sessionId)
    }

    val uploaded = recording?.let {
      transaction(db.database) {
        logger.info { "Re-uploading recording" }
        val result = datastore.upload("${it.guild.id.value}/${mp3File.name}", mp3File)

        it.apply {
          size = result.size
          modifiedOn = this.modifiedOn ?: result.timestamp
          url = result.url
          duration = extractDuration(mp3File)
        }
      }
    } ?: transaction(db.database) {
      Guild.findById(408795211901173762L)?.let {
        logger.info { "Uploading recording and creating dummy Recording record." }
        val result = datastore.upload("${it.id.value}/${mp3File.name}", mp3File)

        Recording.new(sessionId) {
          channel = Channel.findOrCreate(776694242840019016L, "prolonged-testing", 408795211901173762L)
          guild = it
          size = result.size
          modifiedOn = result.timestamp
          url = result.url
          duration = extractDuration(mp3File)
        }
      }
    }

    return uploaded?.also {
      if (mp3File.delete()) {
        logger.info { "Deleted local file ${mp3File.name} after recovery upload." }
      } else {
        logger.warn { "Could not delete local file ${mp3File.name} after recovery upload." }
      }
    }
  }
```

The only change is binding the existing expression to `uploaded` and adding the `return uploaded?.also { ... }` block — the two upload branches are byte-identical to the current code. The delete only happens when a `Recording` row came back (i.e. `datastore.upload` succeeded; on upload failure the exception propagates before the `also`).

- [ ] **Step 4: Run the full test class, verify all pass**

Run: `mvn test -Dtest=PawaTest`
Expected: PASS, including the pre-existing recovery tests. Note: the existing test `it should return existing Recording if MP3 exists` creates an mp3 and recovers it — it asserts on the returned recording, not on the file, so it must still pass with the file now deleted. If it fails, re-read its assertions before changing anything.

- [ ] **Step 5: Update the stability runbook**

In `.agent/runbooks/stability-runbook.md`, in the "2026-07-06 run findings" list, append to the bullet that begins `**Leftover-`.mp3` metric has false positives:**` this sentence:

```
Fixed in code (recovery now deletes the regenerated file after a successful
upload); once deployed, the on-disk count is trustworthy again.
```

And append to the bullet that begins `**Structural flaw found**` this sentence:

```
Fixed in code (attachment failure now falls back to the datastore upload);
after deploy, expect `Discord attachment upload failed, falling back to
datastore` WARNs instead of leftover files.
```

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/tech/gdragon/api/pawa/Pawa.kt src/test/kotlin/tech/gdragon/api/pawa/PawaTest.kt .agent/runbooks/stability-runbook.md
git commit -m "Delete regenerated mp3 after successful recovery upload"
```

---

## Final verification (after both tasks)

- [ ] Run the full build: `mvn test` — everything green (Docker required for `PawaTest`).
- [ ] Confirm no other code paths write or read the recovered `.mp3` after `recoverRecording` returns (already verified during planning: `Pawa.uploadRecording` has no callers outside `recoverRecording`; callers of `recoverRecording` — `Recover.kt:34`, `EventListener.kt:268`, `RecoverRecordingCommand.kt:23`, and the nREPL flow — only use the returned `Recording.url`).
- [ ] Do NOT deploy; leave that to the human (CHANGELOG + release is a separate flow, see CLAUDE.md "Cutting a Release").
