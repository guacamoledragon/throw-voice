# Recover Action Button Implementation Plan

## Overview

Add a "Recover Recording" button to the recording started message, allowing users to trigger recovery directly from the message. This ensures users can only recover recordings from their own server that they could have seen.

## Key Decisions

1. **Button enabled at start** - with a check that skips recovery if bot is still in a voice call (implies recording is still active)
2. **Keep existing commands** (`/recover` and context menu) for trigoman only - no changes needed
3. **Skip guild validation** - the button is embedded in the message, user cannot alter the session ID
4. **Public response** in channel
5. **Basic error message** following existing `/recover` pattern
6. **Use Lang enum** for translations inside `RecordingStartedMessage` (Option 1)

## Files to Modify

| File | Action | Description |
|------|--------|-------------|
| `src/main/kotlin/tech/gdragon/discord/message/RecordingStartedMessage.kt` | CREATE | New embed + button class |
| `src/main/kotlin/tech/gdragon/listener/EventListener.kt` | MODIFY | Add `onButtonInteraction()` handler |
| `src/main/kotlin/tech/gdragon/commands/audio/Record.kt` | MODIFY | Use `RecordingStartedMessage`, change return type |
| `src/main/kotlin/tech/gdragon/BotUtils.kt` | MODIFY | Use `RecordingStartedMessage` in autoRecord, add `sendMessage` overload |

## Implementation Steps

### Step 1: Create `RecordingStartedMessage` class

**New file:** `src/main/kotlin/tech/gdragon/discord/message/RecordingStartedMessage.kt`

- Embed containing:
  - Title: "Recording Started"
  - Description: Recording audio on #channel
  - Session ID field
  - Warning about saving before stopping
- Components:
  - `danger` button with `customId="recover:$sessionId"`, label="Recover Recording"

Uses `Lang` enum and `Babel` to get translated strings.

### Step 2: Add `onButtonInteraction` handler to `EventListener.kt`

- Parse `componentId` with format `recover:{sessionId}`
- Check if bot is in voice call → reply with warning if still recording
- Defer reply (recovery involves database/upload operations)
- Call `pawa.recoverRecording(datastore, sessionId)`
- Reply with `RecordingReply` on success or `ErrorEmbed` on failure

### Step 3: Update `Record.kt`

- Change `handler()` return type from `String` to `MessageCreateData`
- Wrap all string responses in `MessageCreate { content = "..." }`
- Use `RecordingStartedMessage` for successful recording start

### Step 4: Update `BotUtils.kt`

- Update `autoRecord()` to use `RecordingStartedMessage`
- Add `sendMessage(MessageChannel?, MessageCreateData)` overload

---

## Progress Tracker

- [ ] Step 1: Create `RecordingStartedMessage` class
- [ ] Step 2: Add button interaction handler to `EventListener.kt`
- [ ] Step 3: Update `Record.kt` to use new message class
- [ ] Step 4: Update `BotUtils.kt` autoRecord and sendMessage

---

## Notes

(Append implementation notes here as we go)
