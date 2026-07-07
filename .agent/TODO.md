# TODO

## Refactor: Standardize Discord message class naming

The classes in `src/main/kotlin/tech/gdragon/discord/message/` use inconsistent suffixes (`*Reply`, `*Message`, `*Embed`) despite having similar structures. Consider standardizing on `*Reply` since it's the most common pattern:

| Current Name              | Proposed Name           | Notes                                                                              |
|---------------------------|-------------------------|------------------------------------------------------------------------------------|
| `RecordingStartedMessage` | `RecordingStartedReply` | Matches `RecordingReply`                                                           |
| `ErrorEmbed`              | `ErrorReply`            | Also exposes `message`, not just an embed                                          |
| `RequestAccessReply`      | —                       | Already uses `*Reply`, but lacks `val message`; consider adding it for consistency |
| `RequestAccessModal`      | —                       | Fine as-is; it wraps a `Modal`, not a reply                                        |

## Migrate: Replace Launch4j with jpackage for Windows executable

Currently `launch4j.xml` wraps the lite fat JAR into a Windows `.exe` with a bundled `jre/` directory. Migrate to `jpackage` (built into JDK 14+) for a modern, tool-free approach.

Key considerations:
- `jpackage` must run on Windows — requires a Windows GitLab CI runner
- Current Launch4j config sets env vars (`BOT_STANDALONE=true`, etc.) and JVM options (`-Dlog4j.configurationFile=log4j2-lite.xml`) that need to be replicated via `--java-options` and `--arguments`
- `jpackage` can produce both `.exe` (installer) and `app-image` (portable directory with bundled JRE), the latter being closest to the current Launch4j behavior
- Icon is at `assets/pawa-beta.ico`
- Min Java version is 25

## Evaluate: Replace MP3 encoding with Opus for audio recordings

The current pipeline decodes Discord's per-user Opus streams to PCM (via JDA's `handleCombinedAudio`), then re-encodes to MP3 via LAME. Switching to Opus could reduce encoding CPU time and produce smaller files.

Two approaches to evaluate:

1. **Combined PCM → Opus → OGG Opus** — Keep `handleCombinedAudio` for mixed audio, but encode to Opus instead of MP3. Requires an Opus encoder (Concentus for pure Java, or libopus JNI) and VorbisJava (`org.gagravarr:vorbis-java-core`) for OGG container writing. QueueFile approach still works — store raw Opus frame bytes, wrap in OGG at save time.

2. **Per-user raw Opus passthrough** — Use JDA's `handleEncodedAudio(OpusPacket)` to capture raw Opus packets directly from Discord with zero transcoding. Produces one file per speaker instead of a single mixed recording — this is a product-level decision.

Key constraints:
- Opus packets require an OGG container (can't just concatenate like MP3 frames)
- Combined/mixed audio always requires PCM decoding first — there is no "combined Opus" from JDA
- May not address the chipmunk root cause alone (QueueFile I/O pressure is the likely bottleneck, not CPU encoding time), but would be a good optimization on top of the batching fix

## Feature: Localize slash command names and descriptions

Discord supports [localized slash commands](https://discord.com/developers/docs/interactions/application-commands#localization) via `nameLocalizations` and `descriptionLocalizations`. The old prefix `Help` command displayed localized command descriptions based on the guild's `/lang` setting. Now that prefix commands are removed, slash command localization would be the proper way to surface translated command names/descriptions natively in Discord's UI.

JDA supports this via `CommandData.setNameLocalizations()` / `setDescriptionLocalizations()`. The existing `translations*.properties` files already have many of the needed strings.

## Cleanup: Remove `src/assembly` directory

The `src/assembly` directory (docker-compose, `.env`) is out of date and no longer reflects the current deployment setup. Delete it entirely.

## Evaluate: Drop `GatewayIntent.MESSAGE_CONTENT` after prefix removal

With prefix commands gone, the main remaining consumer of the privileged `MESSAGE_CONTENT` intent is the "Recover Recording" message context menu, which reads `event.target.contentRaw` to regex-extract ULID session IDs (`BotUtils.findSessionID()`). Users typically right-click the bot's own `RecordingStartedReply` — and bots always see their own message content regardless of intent — so the intent could potentially be dropped if recovery is refactored to parse only the bot's own embeds, or to store session IDs in message metadata. Note `GUILD_MESSAGES` is still required for `onPrivateMessageReceived`. Dropping a privileged intent reduces Discord verification overhead.

## Release: cut a version documenting prefix command removal

On a separate branch (not the prefix-removal MR), cut the next release that ships the prefix-command clean sweep. Bump `pom.xml` `<version>` (2.17.0 → next, e.g. 2.18.0 — the project does not follow SemVer, so confirm the number) and add a `### Removed` entry to `CHANGELOG.md` noting prefix command support is removed from both Pawa and PawaLite (prefix commands, `Help`/`Prefix`/`RemoveAlias`/`SaveLocation`/`Slash`/`Status`/`Test`, aliases + `/alias`, `BOT_PREFIX_COMMANDS`, and the `prefix` column / `aliases` table drops). The existing `v2.17.0` release tag is the last version that includes prefix support, so it serves as the recovery point — no separate `last-prefix-support` tag needed.

## Feature: Background auto-recovery for failed recordings

Replace the manual `/recover` command (an owner-gated bandaid — see `Recover.kt:27`, restricted to `TRIGOMAN`) with a background process that automatically retries recordings that failed to upload.

When an upload fails, the `.mp3`/`.queue` files are left in `{BOT_DATA_DIR}/recordings/` and the `Recording` row persists with an empty `url`. A background worker could periodically scan for those rows (or leftover files), re-run `pawa.recoverRecording`, and notify the original channel on success — no human in the loop, no Session ID hand-off via the support server.

Re-evaluate priority **after the disconnect-timeout fix (MR !141) is deployed**: bounding the disconnect wait may already eliminate most upload-failure cases, in which case this can stay low priority or be dropped. Decide based on observed failure rate post-deploy — **check the [Recording Upload Failures](https://ui.honeycomb.io/gdragon-d9/environments/prod/board/wPrXbD6xGwN) Honeycomb board** (baseline at creation: ~8.6% of upload attempts failed; the `Upload did not finish` hung-upload panel only populates once MR !141 ships). If the failure rate stays low after deploy, drop this feature. Until then, `/recover` remains the stopgap.

**Update 2026-07-06 (MR !142):** the datastore-fallback MR self-heals every Discord-side upload refusal (400001 / permissions — the cause of all 8 of the week-of-2026-06-29 losses) and stops successful recoveries from leaving `.mp3` residue, so the leftover-`.mp3` count becomes a clean signal. After !142 deploys, the only loss windows left for a worker to cover are (a) datastore also down during the fallback and (b) JVM crash mid-processing. **Next step:** watch the leftover-`.mp3` count for a week or two post-deploy — if it sits at ~0, drop this feature and promote the `.queue` disk-leak cleanup (see runbook "Known separate issues") to the next fix; if it still grows, build the worker scoped to just those two windows.
