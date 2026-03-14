<!-- DO NOT COMMIT: This is a working planning document, not source code. -->

# Deprecate Prefix Commands

## Context

Pawa currently supports two command systems:

1. **Prefix commands** — guild-configurable text prefix (default `!`) + command name, e.g. `!record`, `!save`
2. **Slash commands** — Discord-native `/record`, `/save`, etc.

Both are dispatched through `EventListener` (a single `ListenerAdapter`). Prefix commands
are handled in `onMessageReceived` → `onGuildMessageReceived` → `handleCommand()`, while
slash commands are wired via JDA-KTX `onCommand()` in `Bot.registerSlashCommands()`.

The codebase is deployed as two products from the same source:

|          | **Pawa** (public bot)  | **PawaLite** (self-hosted)          |
|----------|------------------------|-------------------------------------|
| Toggle   | `BOT_STANDALONE=false` | `BOT_STANDALONE=true`               |
| Database | PostgreSQL             | H2 embedded                         |
| Storage  | S3 (Backblaze B2)      | Local filesystem                    |
| Config   | Environment variables  | `settings.properties` override file |

The `BOT_STANDALONE` flag already gates features (auto-record, `/recover` access, nREPL,
tracer). This plan adds a new Koin property to control prefix command availability.

### Why deprecate?

- Discord's ecosystem has moved to slash commands; prefix commands are a legacy UX
- `GatewayIntent.MESSAGE_CONTENT` is a privileged intent — removing prefix commands reduces
  the reasons we need it (though it cannot be fully dropped; see intent analysis below)
- Prefix commands carry maintenance overhead: the `Command` enum, `CommandHandler` abstract
  class, `handleCommand()` dispatch, `usage(prefix)` on every command, `BotUtils.getPrefix()`
  cache, the `prefix` column in `Settings`, `Prefix` command, `RemoveAlias` command, `Help`
  command (DM-based), `SaveLocation` (superseded by `/savedestination`), the `Slash` meta-command,
  and alias resolution — all exist solely for prefix commands

### Inventory of prefix-only commands (no slash equivalent)

| Command        | Purpose                                       | Disposition                              |
|----------------|-----------------------------------------------|------------------------------------------|
| `Help`         | DMs user an embed listing all prefix commands | Remove with prefix system                |
| `Prefix`       | Changes the guild's prefix character          | Remove with prefix system                |
| `RemoveAlias`  | Removes a command alias                       | Remove with prefix system                |
| `SaveLocation` | Sets save channel (prefix-style)              | Already superseded by `/savedestination` |
| `Slash`        | Admin meta-command to register slash cmds     | Remove with prefix system                |
| `Status`       | Debug command (admin-only)                    | Remove with prefix system                |
| `Test`         | Debug command (admin-only)                    | Remove with prefix system                |

### Commands with both prefix and slash

Record, Save, Stop, Alias, AutoRecord, AutoSave, AutoStop, Ignore, Language, Volume, Info
— all share core logic through companion `handler()` methods. Their `CommandHandler.action()`
implementations are the only code that needs prefix-specific maintenance.

---

## `GatewayIntent.MESSAGE_CONTENT` — Cannot be fully dropped

The "Recover Recording" **Message Context Menu** command (`Bot.kt:274` —
`message("Recover Recording")`) reads `event.target.contentRaw` in
`EventListener.onMessageContextInteraction()` (line 74) to regex-extract ULID session IDs
via `BotUtils.findSessionID()`.

Without `MESSAGE_CONTENT`, `contentRaw` is empty for messages not authored by the bot.
In practice, users typically right-click the bot's own `RecordingStartedReply` messages
(bots always see their own message content regardless of intent), so the feature would
*mostly* still work — but it would silently break for any third-party messages containing
session IDs.

**Decision:** Keep `MESSAGE_CONTENT` for now. Revisit only if the context menu recovery
feature is refactored to not depend on raw message content (e.g., by only parsing the bot's
own embeds, or by storing session IDs in message metadata).

`GUILD_MESSAGES` is also retained — it is required to receive `MessageReceivedEvent`s.
Even after prefix commands are removed, the private-message reply handler in
`EventListener.onPrivateMessageReceived()` uses `MessageReceivedEvent`.

**Do NOT modify the `intents` list in `Bot.kt:77-81` in any phase.**

---

## Architecture: Extract prefix handling into a separate listener

### Current state (`Bot.kt:110`)

```kotlin
shardManager.addEventListener(EventListener(pawa), SystemEventListener())
```

`EventListener` handles everything: voice events, guild join/leave, nickname updates,
button interactions, autocomplete, message context menus, slash command logging, *and*
prefix command dispatch.

### Target state

Split `EventListener` into two listeners:

1. **`EventListener`** — retains all non-prefix responsibilities (voice, guild lifecycle,
   button interactions, autocomplete, message context menus, slash command logging, private
   messages)
2. **`PrefixCommandListener`** — new class, handles only `onMessageReceived` for guild
   messages with prefix dispatch

```kotlin
// Bot.kt:110, after refactor
shardManager.addEventListener(EventListener(pawa), SystemEventListener())

if (prefixCommandsEnabled) {
  shardManager.addEventListener(PrefixCommandListener(pawa))
}
```

The `prefixCommandsEnabled` boolean comes from Koin:

```kotlin
// In Bot.kt init block or injected via constructor
val prefixCommandsEnabled: Boolean = getKoin().getProperty("BOT_PREFIX_COMMANDS", "true").toBoolean()
```

### Why Koin property (not PawaConfig)?

`PawaConfig` currently holds domain-level config (`appUrl`, `dataDirectory`, `isStandalone`).
Whether to register a JDA listener is a *wiring* concern, not a domain concern — it belongs
at the composition root in `Bot.kt`, reading directly from the Koin property store. This is
consistent with how `BOT_MAINTENANCE`, `BOT_ACTIVITY`, and `BOT_LEAVE_GUILD_AFTER` are
already consumed: read from `getKoin().getProperty()` at the call site, not threaded through
`PawaConfig`.

---

## Long-term: PawaLite and prefix commands

**Decision:** Clean sweep. Prefix commands will be removed from both Pawa and PawaLite.
Module extraction was evaluated (see below) but the maintenance burden outweighs the
benefit for a solo developer.

If PawaLite customers request prefix command support in the future, the code can be
recovered from git history (last commit with prefix support will be tagged before
the Phase 3 clean sweep). Recovery would require adapting to any API changes that
occurred since removal, but the core logic is preserved in version control.

### Module extraction — evaluated and rejected

A module extraction approach was analyzed in detail. It would require:

| Coupling point | Effort | What's involved |
|----------------|--------|-----------------|
| `Command` enum → registry pattern | MODERATE | Replace hardcoded enum with string-based registry so module can provide handlers |
| 11 dual-mode command files | MODERATE | Split each file: companion object (shared logic) stays in core, class body (prefix action) moves to module |
| `Pawa.createAlias()` | MODERATE | API change from `Command` param to `String`, or move entirely to module |
| `BotUtils.getPrefix/setPrefix` | MINOR | Move to module, trivial `Info.kt` conditional |
| `/alias` slash command registration | MINOR | Module needs hook into `Bot.kt` to register additional slash commands |
| `PrefixCommandListener` | CLEAN | Already isolated, moves wholesale |
| Tests | CLEAN | 4 alias tests move, rest stays |

**Estimated effort:** 5-7 days (vs 1-2 days for clean sweep). The 3-4x overhead
comes from abstraction design, splitting 11 command files, and module build
infrastructure — none of which deliver user-facing value.

---

## Aliases

Aliases are a prefix-only feature. There is no slash command equivalent, and building one
is not planned.

**Pre-deprecation query results (2026-03-12):**

Raw totals: 1,406 guilds / 2,301 aliases. Filtered to **active guilds only**:
280 guilds with 434 aliases. Top aliases are organic user shortcuts (e.g., `r` → RECORD,
`rec` → RECORD, `s` → STOP). No seeded or default aliases exist — all user-created.

**Conclusion:** 280 active guilds is notable but not alarming. No migration path needed.
The deprecation notice should mention that aliases will be removed alongside prefix
commands so those users get a heads-up.

---

## Phased Deprecation Plan

### Timeline

| Phase                                     | Duration        | Cumulative  |
|-------------------------------------------|-----------------|-------------|
| Phase 0: Structural refactor              | 1 release cycle | T+0         |
| Phase 1: Deprecation warnings             | 1 month         | T+1 month   |
| Phase 2: Prefix off by default            | 1 month (trial) | T+2 months  |
| Phase 3: Code removal / module extraction | When ready      | T+3 months+ |

---

### Phase 0: Structural refactor (no behavior change) -- COMPLETE

**Status:** Done. All 27 tests pass after refactor.

**Merge request title:** Extract prefix command dispatch into toggleable PrefixCommandListener

**Merge request description:**
> Isolate prefix command handling from `EventListener` into a new `PrefixCommandListener` class, registered conditionally via the `BOT_PREFIX_COMMANDS` Koin property (defaults to `"true"`).
> No behavior change — this is a structural refactor preparing for prefix command deprecation. Setting `BOT_PREFIX_COMMANDS=false` disables all prefix commands while slash commands continue to work.

**Goal:** Isolate prefix command handling into a toggleable listener.

**Preconditions:** All 27 tests pass (`mvn test`). Baseline: 0 failures.

#### Step 0.1: Create `PrefixCommandListener.kt`

Create a new file at:
```
src/main/kotlin/tech/gdragon/listener/PrefixCommandListener.kt
```

This class takes over prefix command dispatch from `EventListener`. It must:

- Extend `ListenerAdapter()`
- Implement `KoinComponent`
- Accept `Pawa` as a constructor parameter
- Override `onMessageReceived(event: MessageReceivedEvent)`

**Methods to move from `EventListener.kt` into `PrefixCommandListener`:**

| Method                       | Lines in EventListener.kt | Visibility in EventListener  | Notes                                                  |
|------------------------------|---------------------------|------------------------------|--------------------------------------------------------|
| `onMessageReceived()`        | 369-375                   | `override` (public)          | The router. Override it in the new class.              |
| `onGuildMessageReceived()`   | 226-282                   | `fun` (public, not override) | Move verbatim. This is the prefix dispatch logic.      |
| `onPrivateMessageReceived()` | 356-367                   | `private fun`                | Move verbatim. Make it `private` in the new class too. |

**Fields to copy (not move) from `EventListener.kt` into `PrefixCommandListener`:**

| Field     | Line in EventListener.kt | Notes                                                                       |
|-----------|--------------------------|-----------------------------------------------------------------------------|
| `logger`  | 42                       | Each class needs its own logger instance.                                   |
| `website` | 43                       | Read from `getKoin().getProperty("BOT_WEBSITE", "http://localhost:8080/")`. |

**Imports required by `PrefixCommandListener`** (subset of `EventListener`'s imports):

```kotlin
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.koin.core.component.KoinComponent
import tech.gdragon.BotUtils
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.commands.handleCommand
```

#### Step 0.2: Remove moved methods from `EventListener.kt`

After creating `PrefixCommandListener`, remove these methods from `EventListener`:

- `onMessageReceived()` (lines 369-375) — **delete entirely**
- `onGuildMessageReceived()` (lines 226-282) — **delete entirely**
- `onPrivateMessageReceived()` (lines 356-367) — **delete entirely**

Then clean up unused imports from `EventListener.kt`. After removal, these imports
become unused and should be removed:

```kotlin
import io.opentelemetry.api.trace.StatusCode          // only used by onGuildMessageReceived
import io.opentelemetry.api.trace.Tracer               // only used by onGuildMessageReceived
import net.dv8tion.jda.api.entities.channel.ChannelType // only used by onMessageReceived
import tech.gdragon.commands.InvalidCommand             // only used by onGuildMessageReceived
import tech.gdragon.commands.handleCommand              // only used by onGuildMessageReceived
```

**Keep** these imports even though they may look like candidates for removal:
- `net.dv8tion.jda.api.events.message.MessageReceivedEvent` — **remove** (no longer used)
- `io.github.oshai.kotlinlogging.withLoggingContext` — **keep** (used by voice/guild handlers)
- `tech.gdragon.BotUtils` — **keep** (used by voice handlers, guild join, nickname, etc.)

**Warning about `tech.gdragon.commands.logger`:** `EventListener.onSlashCommandInteraction()`
(line 398) references `tech.gdragon.commands.logger` — this is the **file-level** `val logger`
defined in `CommandHandler.kt:28`. This reference must continue to compile. Do NOT remove
the `import` for it (there is no explicit import — it's accessed via the fully-qualified
name `tech.gdragon.commands.logger`). No changes needed here, just be aware.

The `website` field on `EventListener` (line 43) is still used by
`onMessageContextInteraction` — **do NOT remove it** from `EventListener`.

Wait — actually check: `website` is only used inside `onGuildMessageReceived` (the
maintenance message at line 265). After moving that method out, verify whether `website`
is still referenced anywhere in `EventListener`. If not, remove it. If the
`onMessageContextInteraction` handler or any other remaining method uses it, keep it.

Checking: `onMessageContextInteraction` (lines 67-123) does NOT reference `website`.
No other remaining method references `website`. **Remove the `website` field from
`EventListener`** after moving the methods.

#### Step 0.3: Update `Bot.kt` to conditionally register `PrefixCommandListener`

**File:** `src/main/kotlin/tech/gdragon/discord/Bot.kt`

**Current code (line 108-111):**
```kotlin
      // Register Listeners
      shardManager.addEventListener(EventListener(pawa), SystemEventListener())
      registerSlashCommands()
```

**Replace with:**
```kotlin
      // Register Listeners
      shardManager.addEventListener(EventListener(pawa), SystemEventListener())

      val prefixCommandsEnabled = getKoin().getProperty("BOT_PREFIX_COMMANDS", "true").toBoolean()
      if (prefixCommandsEnabled) {
        shardManager.addEventListener(PrefixCommandListener(pawa))
      }

      registerSlashCommands()
```

**Add import** to `Bot.kt`:
```kotlin
import tech.gdragon.listener.PrefixCommandListener
```

**Do NOT modify:**
- The `intents` list (lines 77-81) — `MESSAGE_CONTENT` and `GUILD_MESSAGES` stay
- The `Command` enum (lines 294-352) — unchanged
- `registerSlashCommands()` or `updateSlashCommands()` — unchanged

#### Step 0.4: Verify

Run:
```sh
mvn test
```

**Expected:** All 27 tests pass, 0 failures. The test suite does not exercise the
listener registration path, so the refactor should be transparent.

**Compile check:** The project must compile without errors:
```sh
mvn compile
```

**Manual testing (if a test bot is available):**

1. With `BOT_PREFIX_COMMANDS=true` (default): verify `!record`, `!help`, etc. work
2. With `BOT_PREFIX_COMMANDS=false` (set via env var): verify the bot ignores `!record`
   etc. while slash commands `/record`, `/save` still work

#### Phase 0 summary — files touched

| File                                                             | Action                                                                   |
|------------------------------------------------------------------|--------------------------------------------------------------------------|
| `src/main/kotlin/tech/gdragon/listener/PrefixCommandListener.kt` | **Create** — new file                                                    |
| `src/main/kotlin/tech/gdragon/listener/EventListener.kt`         | **Edit** — remove 3 methods, remove `website` field, clean up imports    |
| `src/main/kotlin/tech/gdragon/discord/Bot.kt`                    | **Edit** — add conditional `PrefixCommandListener` registration + import |

**No other files are modified in Phase 0.**

---

### Phase 1: Deprecation warnings (1 month) -- COMPLETE

**Status:** Merged via [MR !124](https://gitlab.com/pawabot/pawa/-/merge_requests/124) on 2026-03-12.

**Goal:** Warn prefix command users that the feature is going away. Be noisy to accelerate
migration to slash commands.

**Preconditions:** Phase 0 is complete. `PrefixCommandListener` exists and is registered.

#### Step 1.1: Add deprecation follow-up message -- DONE

**File:** `src/main/kotlin/tech/gdragon/listener/PrefixCommandListener.kt`

After every successful prefix command, a deprecation notice is sent as a separate message.
Gated on `!pawa.isStandalone`. Uses Discord dynamic timestamps (`<t:1777593600:D>` and
`<t:1777593600:R>`) so the date renders in each user's locale with a countdown.

#### Step 1.2: Update Help command with deprecation banner -- DONE

**File:** `src/main/kotlin/tech/gdragon/commands/misc/Help.kt`

The Help embed now includes a `setDescription()` deprecation banner with the same dynamic
timestamp format. Gated on `!pawa.isStandalone`.

#### Step 1.3: Run alias usage query

Connect to the production PostgreSQL database and run:

```sql
SELECT COUNT(DISTINCT settings_id) AS guilds_with_aliases,
       COUNT(*) AS total_aliases
FROM aliases;
```

Document the results. Expected: low usage, confirming no migration path needed.

**Note:** Pre-deprecation results already documented in the Aliases section above
(280 active guilds, 434 aliases). Re-run after Phase 1 has been live for a month.

#### Step 1.4: External announcements (human task, not code)

- Post deprecation notice on pawa.im
- Post in support Discord server
- Include the sunset date (May 1, 2026)

#### Step 1.5: Add `aliased` span attribute -- DONE (added during Phase 1)

**File:** `src/main/kotlin/tech/gdragon/commands/CommandHandler.kt`

Added a boolean `aliased` attribute to the tracing span in `handleCommand()`. When a
prefix command is resolved via an alias, `aliased=true` is set on the parent span. This
enables querying alias usage in Honeycomb (e.g., `COUNT WHERE aliased = true GROUP BY command`).

#### Step 1.6: Add deprecation warnings to pawa.im docs -- DONE (added during Phase 1)

**Files:** `src/site/commands.md` + all 16 files in `src/site/commands/prefix/`

Added `!>` deprecation banners (docsify danger callout) to the commands hub page above the
prefix commands table, and to each individual prefix command documentation page.

#### Phase 1 summary — files touched

| File                                                             | Action                                                          |
|------------------------------------------------------------------|-----------------------------------------------------------------|
| `src/main/kotlin/tech/gdragon/listener/PrefixCommandListener.kt` | **Edit** — deprecation message after `handleCommand()`          |
| `src/main/kotlin/tech/gdragon/commands/misc/Help.kt`             | **Edit** — deprecation banner in embed description              |
| `src/main/kotlin/tech/gdragon/commands/CommandHandler.kt`        | **Edit** — `aliased` span attribute for Honeycomb observability |
| `src/site/commands.md`                                           | **Edit** — `!>` deprecation warning above prefix table          |
| `src/site/commands/prefix/*.md` (16 files)                       | **Edit** — `!>` deprecation warning on each page               |

---

### Phase 2: Data collection & prefix off by default (1 month trial)

**Goal:** Collect data on prefix vs slash usage to confirm readiness, then disable prefix
commands for Pawa public. Monitor for issues.

**Preconditions:** Phase 1 shipped at least 1 month ago.

#### Step 2.0: Honeycomb monitoring board -- DONE

**Board:** [Prefix Command Deprecation](https://ui.honeycomb.io/gdragon-d9/environments/prod/board/5tndTJUpBgp)
**Tag:** `project:prefix-deprecation`

The board has four panels:

| Panel | Type | What it shows |
|-------|------|---------------|
| Prefix Command Deprecation Tracker | Text | Context, key question, field conventions |
| Prefix Command % (of all commands) | Line | `prefix_pct` formula — watch for downward trend after warnings |
| Prefix Commands by Type | Stacked area | Breakdown of which prefix commands are used (RECORD, SAVE, STOP, etc.) |
| Unique Guilds Using Prefix Commands | Line | COUNT_DISTINCT(guild) — how many guilds would be affected |

**Conventions:** Prefix commands are UPPERCASE in the `command` span attribute (e.g. `RECORD`),
slash commands are lowercase (e.g. `record`). The board uses `REG_MATCH($command, "^[A-Z]+$")`
as a calculated field `is_prefix` to distinguish them.

**Baseline snapshot (2026-03-13, 7-day window):**
- 14,058 prefix commands vs 5,074 slash commands = **73.5% prefix**
- 256 unique guilds using prefix commands
- Top prefix commands: SAVE (6,876), RECORD (6,514), STOP (555), HELP (61)

**`aliased` attribute:** The `aliased` boolean span attribute was added in Phase 1 but hasn't
been deployed yet. Once deployed, add a fifth panel to the board:
- Filter: `aliased = true`, breakdown by `command`, COUNT
- This will show how many commands are invoked via aliases and which ones

#### Step 2.1: Review data and decide (human task)

After Phase 1 has been live for ~1 month, review the Honeycomb board:
- Is `prefix_pct` trending down?
- How many unique guilds are still using prefix commands?
- Is alias usage negligible?

If the numbers look acceptable, proceed to flip the switch.

#### Step 2.2: Set deployment config

This is a **deployment/ops change**, not a code change. Set the environment variable
in the Pawa public deployment:

```
BOT_PREFIX_COMMANDS=false
```

The in-code default in `Bot.kt` remains `"true"` — PawaLite is unaffected.

Where to set it depends on deployment method:
- If Docker/docker-compose: add to environment section
- If `.gitlab-ci.yml` deploy job: add to variables
- If systemd/shell: add to environment file

#### Step 2.3: Remove Phase 1 deprecation code (optional cleanup)

Since `PrefixCommandListener` is no longer registered for the public bot, the
deprecation message code from Step 1.1 is unreachable. It can be left in place
(it's harmless — PawaLite doesn't show it because `pawa.isStandalone == true`)
or removed for cleanliness.

If removing:
- Revert the `!pawa.isStandalone` block added in Step 1.1 of `PrefixCommandListener.kt`
- Revert the `setDescription` added in Step 1.2 of `Help.kt`

#### Step 2.4: Monitor (human task)

- Watch support Discord for user complaints for 1 month
- Watch the Honeycomb board — prefix command count should drop to zero for the public bot
- If critical issues arise, re-enable by setting `BOT_PREFIX_COMMANDS=true`

#### Step 2.5: Update documentation (human task)

- Remove prefix command references from pawa.im docs
- Update any README or user-facing documentation

#### Phase 2 summary — files touched

No code changes required (deployment config only, plus Honeycomb board).
Optional cleanup of 2 files if removing Phase 1 deprecation code.

---

### Phase 3: Code removal (clean sweep)

**Goal:** Remove all prefix command code from the codebase. Both Pawa and PawaLite.

**Preconditions:** Phase 2 has been stable for at least 1 month.

**Before starting:** Tag the last commit with prefix support (e.g., `last-prefix-support`)
so the code can be recovered from git history if PawaLite customers request it in the future.

Execute these steps **in order** — later steps depend on earlier deletions compiling.

**Step 3.1: Delete `PrefixCommandListener`**

Delete file:
```
src/main/kotlin/tech/gdragon/listener/PrefixCommandListener.kt
```

Remove from `Bot.kt`:
- The `prefixCommandsEnabled` variable (line 114, uses `getKoin().getBooleanProperty("BOT_PREFIX_COMMANDS")`)
- The `if` block that registers it (lines 115-117)
- `import tech.gdragon.listener.PrefixCommandListener` (line 40)
- `import tech.gdragon.koin.getBooleanProperty` (line 24, if no other callers)

**Step 3.2: Delete prefix-only command files and `Alias.kt`**

Delete these files:
```
src/main/kotlin/tech/gdragon/commands/misc/Help.kt
src/main/kotlin/tech/gdragon/commands/settings/Alias.kt
src/main/kotlin/tech/gdragon/commands/settings/Prefix.kt
src/main/kotlin/tech/gdragon/commands/settings/RemoveAlias.kt
src/main/kotlin/tech/gdragon/commands/settings/SaveLocation.kt
src/main/kotlin/tech/gdragon/commands/slash/Slash.kt
src/main/kotlin/tech/gdragon/commands/debug/Status.kt
src/main/kotlin/tech/gdragon/commands/debug/Test.kt
```

**Note:** `Alias.kt` is deleted entirely (not stripped of overrides like other dual-mode
commands) because the `/alias` slash command only creates aliases for prefix commands.
With prefix commands gone, aliases are dead data.

**Step 3.3: Remove `CommandHandler` abstract class**

Delete file:
```
src/main/kotlin/tech/gdragon/commands/CommandHandler.kt
```

This removes:
- `abstract class CommandHandler` (lines 16-24)
- `data class InvalidCommand` (line 26)
- `val logger` (line 28) — **WARNING:** this file-level logger is referenced by
  `EventListener.onSlashCommandInteraction()` at **line 316** via
  `tech.gdragon.commands.logger`. Before deleting, either:
  - (a) Move `val logger = KotlinLogging.logger {}` to a new file
    (e.g., `src/main/kotlin/tech/gdragon/commands/Logging.kt` in package
    `tech.gdragon.commands`) so the FQN `tech.gdragon.commands.logger` still resolves, OR
  - (b) Replace the reference in `EventListener.kt:316` with the class's own `logger`.
- `fun handleCommand()` (lines 30-73)

**Step 3.4: Remove `action()`, `usage()`, `description()` from dual-mode commands**

For each of these files, remove the `CommandHandler` superclass, the `: CommandHandler()`
inheritance, and the `override fun action(...)`, `override fun usage(...)`,
`override fun description(...)` methods. Keep only the `companion object` with `command`
and `slashHandler()`. The class may become an `object` if it has no instance state.

Files:
```
src/main/kotlin/tech/gdragon/commands/audio/Record.kt
src/main/kotlin/tech/gdragon/commands/audio/Save.kt
src/main/kotlin/tech/gdragon/commands/audio/Stop.kt
src/main/kotlin/tech/gdragon/commands/settings/AutoRecord.kt
src/main/kotlin/tech/gdragon/commands/settings/AutoSave.kt
src/main/kotlin/tech/gdragon/commands/settings/AutoStop.kt
src/main/kotlin/tech/gdragon/commands/settings/Ignore.kt
src/main/kotlin/tech/gdragon/commands/settings/Language.kt
src/main/kotlin/tech/gdragon/commands/settings/Volume.kt
src/main/kotlin/tech/gdragon/commands/slash/Info.kt
```

**Note:** `Alias.kt` is NOT in this list — it was deleted entirely in Step 3.2.
That's 10 files, not 11.

**Step 3.5: Delete the `Command` enum and `/alias` slash command**

In `Bot.kt`:
- Delete the entire `enum class Command` (lines 302-360)
- Remove the `/alias` `onCommand()` block in `registerSlashCommands()` (starts at line 193)
- Remove the `/alias` entry in `updateSlashCommands()` (around line 268)
- Remove the `AliasTranslator` import alias (line 45)
- Remove `import tech.gdragon.commands.CommandHandler` (now unused)

**Step 3.6: Delete `Pawa.createAlias()` and alias tests**

In `src/main/kotlin/tech/gdragon/api/pawa/Pawa.kt`:
- Delete the `createAlias()` method entirely (lines 54-80)
- Remove `import tech.gdragon.discord.Command`

In `src/test/kotlin/tech/gdragon/api/pawa/PawaTest.kt`:
- Delete the entire `context("when alias")` block (lines 50-72, 4 tests)
- Remove `import tech.gdragon.discord.Command`

**Why delete instead of refactor:** The old plan called for changing the `createAlias()`
signature from `Command` to `String`. But since `/alias` slash command is also being
removed (Step 3.5) and aliases only work with prefix commands, there are no remaining
callers. Delete the method and its tests entirely.

**Step 3.7: Remove prefix-related `BotUtils` methods**

In `src/main/kotlin/tech/gdragon/BotUtils.kt`, delete:
- `guildCache` field (line 63)
- `getPrefix()` method (lines 166-178)
- `setPrefix()` method (lines 180-188)

Also check `Info.kt` — `Info.retrieveInfo()` displays `settings?.prefix` in the info
embed. Remove or replace that field with a static value or omit it.

**Step 3.8: Database migrations**

Create Flyway migration files to:
1. Drop the `aliases` table (must be first — it has a FK to `settings`)
2. Drop the `prefix` column from `settings` table

**Migration file locations** (the plan previously had the wrong path):
- Postgres: `sql/postgres/`
- Shared (H2 + Postgres): `sql/common/`
- H2-only: `src/main/resources/h2/`

Follow existing naming convention: `V{YYYYMMDDHHMMSS}__{description}.sql`
(e.g., `V20260501000000__drop-aliases-table.sql`).

**Step 3.9: Update DB DAOs**

In `src/main/kotlin/tech/gdragon/db/dao/DataAccessObject.kt`:
- Remove the `Alias` DAO class (lines 21-37)
- Remove `var prefix by SettingsTable.prefix` from the `Settings` DAO (line 138)
- Remove `val aliases by Alias referrersOn Aliases.settings` from the `Settings` DAO (line 144)

In `src/main/kotlin/tech/gdragon/db/table/Tables.kt`:
- Remove `val prefix` from `object Settings` (line 27)
- Remove `object Aliases` table definition (lines 34-38)

**Step 3.10: Remove translation keys**

In all `src/main/resources/translations*.properties` files (15 files), remove:
- All `*.usage` keys (14 keys — these are prefix command usage strings)
- `help.*` keys (help command is deleted)
- `prefix.*` keys (prefix command is deleted)
- `alias.*` keys (alias command is deleted)
- `removealias.*` keys (removealias command is deleted)
- `savelocation.*` keys (savelocation command is deleted)
- `slash.*` keys (slash command is deleted)

Translation files:
```
translations.properties          translations_hu.properties
translations_de.properties       translations_id.properties
translations_es.properties       translations_in_ID_indonesia.properties
translations_fil.properties      translations_ita.properties
translations_fr.properties       translations_pl.properties
translations_pt_BR.properties    translations_th.properties
translations_tha.properties      translations_vi.properties
translations_zh.properties
```

**Step 3.11: Remove `BOT_PREFIX_COMMANDS` references**

Grep for `BOT_PREFIX_COMMANDS` and remove any remaining references. After Step 3.1,
this should only remain in:
- Koin property definitions (e.g., `App.kt` or wherever properties are loaded)
- Any deployment config or documentation

**Step 3.12: Remove pawa.im prefix command docs**

Delete the prefix command documentation pages and remove the prefix commands section
from the commands hub:

Delete directory:
```
src/site/commands/prefix/
```

In `src/site/commands.md`:
- Remove the entire `## Prefix Commands` section (heading + deprecation warning + table)

In `src/site/quickstart.md`:
- Update any `!record`/`!save` examples to use slash commands (`/record`, `/save`)

**Step 3.13: Verify**

```sh
mvn compile   # must compile cleanly
mvn test      # all tests pass (test count will decrease — alias tests removed)
```

#### Phase 3 summary — files touched

| File                                                             | Action                                                                   |
|------------------------------------------------------------------|--------------------------------------------------------------------------|
| `src/main/kotlin/tech/gdragon/listener/PrefixCommandListener.kt` | **Delete**                                                               |
| `src/main/kotlin/tech/gdragon/commands/CommandHandler.kt`        | **Delete** (after extracting logger)                                     |
| `src/main/kotlin/tech/gdragon/commands/misc/Help.kt`             | **Delete**                                                               |
| `src/main/kotlin/tech/gdragon/commands/settings/Alias.kt`        | **Delete** (entire file — aliases are prefix-only)                       |
| `src/main/kotlin/tech/gdragon/commands/settings/Prefix.kt`       | **Delete**                                                               |
| `src/main/kotlin/tech/gdragon/commands/settings/RemoveAlias.kt`  | **Delete**                                                               |
| `src/main/kotlin/tech/gdragon/commands/settings/SaveLocation.kt` | **Delete**                                                               |
| `src/main/kotlin/tech/gdragon/commands/slash/Slash.kt`           | **Delete**                                                               |
| `src/main/kotlin/tech/gdragon/commands/debug/Status.kt`          | **Delete**                                                               |
| `src/main/kotlin/tech/gdragon/commands/debug/Test.kt`            | **Delete**                                                               |
| `src/main/kotlin/tech/gdragon/discord/Bot.kt`                    | **Edit** — remove `Command` enum, `/alias` slash cmd, listener reg       |
| `src/main/kotlin/tech/gdragon/api/pawa/Pawa.kt`                  | **Edit** — delete `createAlias()` method entirely                        |
| `src/main/kotlin/tech/gdragon/BotUtils.kt`                       | **Edit** — remove `getPrefix`, `setPrefix`, `guildCache`                 |
| `src/main/kotlin/tech/gdragon/db/dao/DataAccessObject.kt`        | **Edit** — remove `prefix`, `aliases`, `Alias` DAO                       |
| `src/main/kotlin/tech/gdragon/db/table/Tables.kt`                | **Edit** — remove `prefix` column, `Aliases` table                       |
| `src/main/kotlin/tech/gdragon/listener/EventListener.kt`         | **Edit** — fix `tech.gdragon.commands.logger` reference (line 316)       |
| `src/main/kotlin/tech/gdragon/commands/audio/Record.kt`          | **Edit** — remove `CommandHandler` inheritance and overrides             |
| `src/main/kotlin/tech/gdragon/commands/audio/Save.kt`            | **Edit** — same                                                          |
| `src/main/kotlin/tech/gdragon/commands/audio/Stop.kt`            | **Edit** — same                                                          |
| `src/main/kotlin/tech/gdragon/commands/settings/AutoRecord.kt`   | **Edit** — same                                                          |
| `src/main/kotlin/tech/gdragon/commands/settings/AutoSave.kt`     | **Edit** — same                                                          |
| `src/main/kotlin/tech/gdragon/commands/settings/AutoStop.kt`     | **Edit** — same                                                          |
| `src/main/kotlin/tech/gdragon/commands/settings/Ignore.kt`       | **Edit** — same                                                          |
| `src/main/kotlin/tech/gdragon/commands/settings/Language.kt`     | **Edit** — same                                                          |
| `src/main/kotlin/tech/gdragon/commands/settings/Volume.kt`       | **Edit** — same                                                          |
| `src/main/kotlin/tech/gdragon/commands/slash/Info.kt`            | **Edit** — same + remove prefix display from info embed                  |
| `src/test/kotlin/tech/gdragon/api/pawa/PawaTest.kt`              | **Edit** — delete alias test context (4 tests), remove `Command` import  |
| `sql/postgres/V*__drop-aliases-table.sql`                        | **Create** — Flyway migration                                            |
| `sql/postgres/V*__drop-prefix-column.sql`                        | **Create** — Flyway migration                                            |
| `sql/common/` or `src/main/resources/h2/`                        | **Create** — matching H2 migrations if needed                            |
| `src/main/resources/translations*.properties` (15 files)         | **Edit** — remove `*.usage`, `help.*`, `prefix.*`, `alias.*`, etc.       |
| `src/site/commands/prefix/` (16 files)                           | **Delete** — entire directory                                            |
| `src/site/commands.md`                                           | **Edit** — remove prefix commands section                                |
| `src/site/quickstart.md`                                         | **Edit** — update examples from `!` prefix to `/` slash                  |

---

## Configuration Summary

| Property              | Phases 0-1  | Phase 2         | Phase 3     |
|-----------------------|-------------|-----------------|-------------|
| `BOT_PREFIX_COMMANDS` | `true`      | `false` (deploy) | **Removed** |

After Phase 3, the `BOT_PREFIX_COMMANDS` property no longer exists. PawaLite also
drops prefix support.

---

## Resolved Questions

1. **PawaLite long-term:** Clean sweep — prefix commands removed from both Pawa and
   PawaLite. Module extraction was evaluated (~5-7 days, 3-4x the effort of clean sweep)
   and rejected to minimize maintenance burden. If PawaLite customers request prefix
   support in the future, the code can be recovered from git history (tag the last commit
   before Phase 3 clean sweep).

2. **Deprecation notice delivery:** Separate follow-up message (noisy). Intentionally
   annoying to motivate migration. Gated on `!pawa.isStandalone`. Uses Discord dynamic
   timestamps for localized date display. Temporary — removed when prefix commands are
   disabled in Phase 2.

3. **Sunset timeline:** 1 month warning (Phase 1), data collection + prefix off (Phase 2),
   then code removal (Phase 3).

4. **Aliases:** Removed with prefix commands. The `/alias` slash command is also removed
   since it only creates aliases for prefix commands. Pre-deprecation data: 280 active
   guilds with 434 aliases. No slash alias replacement planned.

5. **`GatewayIntent.MESSAGE_CONTENT`:** Kept. Required by the "Recover Recording" Message
   Context Menu command which reads `event.target.contentRaw` to extract session IDs.
   `GUILD_MESSAGES` also kept for `onPrivateMessageReceived`. Neither can be dropped
   after prefix removal.
