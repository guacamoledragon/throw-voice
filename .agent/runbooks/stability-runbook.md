# Pawa Stability Runbook

A standing guide for a periodic coding agent to (1) check pawa's production
stability on Honeycomb, (2) keep narrowing the root cause of *recordings that
never reach the user*, and (3) drive toward the goal below. Self-contained —
you should not need other docs to run it.

## The goal (definition of done)

**No user should ever need to run `/recover`.** When this is true and stays
true, we're done.

Exception (deferred, out of scope): if we **stop/restart pawa while a user is
actively recording**, that recording can be lost — accepted for now.

Companion docs: [`../plans/production-stability-debugging.md`](../plans/production-stability-debugging.md)
(investigation history) and the "Background auto-recovery" item in
[`../TODO.md`](../TODO.md).

## What's true right now (2026-07-06 baseline)

Deployed: legacy recorder removed; `-XX:ErrorFile` → mounted volume; bounded
`disconnect` upload wait (MR !141, 60s timeout); inactive-guild purge complete
(post-purge restart happened: **13 shards, 12,479 guilds**).

**2026-07-06 run findings (major update to the picture):**

- **The week's losses were NOT crash-induced.** All 8 genuinely un-delivered
  recordings (Jul 4–5) have an explicit `Error uploading recording` ERROR with
  a Discord-side exception: 5× `400001: Access to file uploads has been limited
  for this guild` (guilds "Loud Nation" ×3, ".gg/revolutionary" ×2), 3×
  permission errors (`MESSAGE_SEND`, `MESSAGE_ATTACH_FILES`, `VIEW_CHANNEL`).
  The crash-induced-loss hypothesis is **refuted for this week**.
- **Structural flaw found** (`SharedAudioRecorder.uploadRecording`,
  SharedAudioRecorder.kt:98): an exception from the Discord attachment upload
  aborts the whole method — no datastore/S3 fallback for small files, no local
  cleanup, no `url` in DB. Every Discord-side upload error therefore requires
  `/recover`. Fixing this (fall back to datastore + APP_URL link when the
  attachment upload throws) would have delivered all 8 of this week's losses.
  Fixed in code (attachment failure now falls back to the datastore upload);
  after deploy, expect `Discord attachment upload failed, falling back to
  datastore` WARNs instead of leftover files.
- **`hs_err` dumps were silently lost until now:** `-XX:ErrorFile` pointed at
  `${BOT_DATA_DIR}/dumps/` but the directory never existed and the JVM doesn't
  create it. Fixed 2026-07-06 (`mkdir /opt/pawa/data/dumps`, mode 777). Dumps
  are only capturable from now on.
- **Leftover-`.mp3` metric has false positives:** manual `/recover` re-generates
  the `.mp3` from the queue file, uploads it, but never deletes the regenerated
  local file. 3 of the 11 on-disk `.mp3` were successful-recovery residue
  (matching `Recovering <ulid> from queue file` → `Finished uploading`). When
  counting, cross-check each ULID's session logs.
  Fixed in code (recovery now deletes the regenerated file after a successful
  upload); once deployed, the on-disk count is trustworthy again.
- **`Upload did not finish within 60s` WARNs are not real hangs:** all 4
  occurrences this week were large files (37–44 MB) that finished uploading
  seconds after the WARN and cleaned up normally. MR !141 works; the 60s bound
  is just tight for big uploads.

The native crash (SIGSEGV in `opus_decoder_get_nb_samples`, upstream DAVE/jdave
defect — [JDA #2998](https://github.com/discord-jda/JDA/issues/2998); latest
JDA 6.4.2 + jdave 0.1.8, no fix, DAVE can't be disabled) still exists and
restarts continue (~1/day), but it was not the cause of this week's losses.

Baselines to compare against each run:

| Metric | 2026-06-27 | 2026-07-06 | Source |
|---|---|---|---|
| Bot restarts | ~2.3/day | **~1.0/day** (7 in the week) | logs `ONLINE: Connected to` |
| Hung uploads (`Upload did not finish`) | 0 | 4/week — all self-resolved seconds later | logs |
| **Leftover `.mp3` (truly un-delivered)** | **10** | **8** (11 on disk − 3 recovery residue) | SSH + session-log cross-check |
| Leftover `.queue` on disk (disk leak) | ~1,247 | 1,155 | SSH |
| `hs_err` crash dumps captured | 0 | 0 (dir was missing; fixed, watch now) | SSH |
| Shards / guilds | 17 / ~12k | 13 / 12,479 | logs |

## Access (all read-only unless noted)

**Honeycomb** (MCP server `honeycomb` is allow-listed — no prompts). Team
`gdragon-d9`, env `prod`, dataset `pawa` (structured logs; each row is a log
event with `message`, `log.level`, `log.logger`, `session-id`, `guild`,
`jda.shard.id`).
- Crashes & Restarts board: https://ui.honeycomb.io/gdragon-d9/environments/prod/board/cnGuQpvA961
- Recording Upload Failures board: https://ui.honeycomb.io/gdragon-d9/environments/prod/board/wPrXbD6xGwN

**Production host** (`pawa.im`, SSH). The bot container is `pawa_bot_1`
(distroless — no shell inside); Postgres is `pawa_database_1`.
- Live logs: `ssh pawa.im 'cd /opt/pawa && docker compose logs -n 200 bot'`
  or `ssh pawa.im 'tail -n 200 /opt/pawa/logs/app.json'`
- DB (read-only, SELECT/`\d` only):
  `ssh pawa.im 'set -a; . /opt/pawa/.env; docker exec -i pawa_database_1 psql -U "$DB_USER" -d "$DB_NAME"' <<'SQL' … SQL`
- Crash dumps (host): `/opt/pawa/data/dumps/` (= `/app/data/dumps` in container).
- Recordings on disk (host): `/opt/pawa/data/recordings/` (= `/app/data/recordings`).

Never run writes/UPDATE/DELETE on prod. The inactive-guild leave flow and any
recovery are driven by the human via nREPL (`src/main/clojure/repl.clj`), not by
this agent.

## Periodic check (run this each time, report deltas vs baseline)

1. **Un-delivered recordings (ground truth).** This is the metric that maps
   directly to the goal — a `.mp3` only lingers when a save's upload failed
   (success deletes it):
   ```sh
   ssh pawa.im 'ls -1 /opt/pawa/data/recordings/*.mp3 2>/dev/null | wc -l'
   ```
   Trend this toward 0. If it climbs, recordings are being lost faster than
   recovered. (Ignore `.queue` count for the goal — those leak on every
   recording regardless of success; see "Disk leak" below.)

2. **Crash dumps.** The single most valuable artifact — still none captured:
   ```sh
   ssh pawa.im 'ls -la /opt/pawa/data/dumps/'
   ```
   If an `hs_err_pid*.log` appears → go to "When a crash dump lands" below.

3. **Crashes & Restarts board.** Is the restart rate above ~2.3/day? Each full
   restart drops all shards and can lose an in-flight recording/upload. Note any
   spike and its timing.

4. **Recording Upload Failures board.** Check the failure rate and especially
   the **`Upload did not finish`** panel — now that MR !141 is deployed, a
   non-zero value here means uploads are *genuinely hanging* (not crashing). Zero
   here while leftover `.mp3` keeps growing means losses are **crash-induced**,
   not hangs.

5. **Honeycomb queries** (env `prod`, dataset `pawa`) if you need raw numbers —
   filter `message` with `starts-with`:
   - Upload failures: `Error uploading recording`
   - Processing failures: `Failed to process completed recording`
   - Hung uploads (MR !141 signal): `Upload did not finish`
   - Restarts: `ONLINE: Connected to`
   - Gateway churn: `Got disconnected from WebSocket` / `Reconnect failed` / `Missed`

## Disambiguate the open problem: hang vs. crash-induced loss

Both leave an un-uploaded `.mp3` on disk and produce a recovery request. Tell
them apart — the fix differs:

- **Hang** → there IS an `Upload did not finish within 60s…` WARN
  (`tech.gdragon.listener.BaseAudioRecorder`) for that `session-id`, and **no
  restart** near that time. Root cause is a stuck network call. Next step:
  instrument `BotUtils.uploadFile` (Discord) and `datastore.upload` (S3/Minio) in
  `SharedAudioRecorder.uploadRecording` with timeouts + timing logs to find which
  call hangs.

- **Crash-induced loss** → **no** hung-upload WARN for that session, but a
  **restart** (`ONLINE: Connected to`) lands shortly after the recording's
  `saveRecording completed (upload in background)` log. The background upload
  thread was killed by the JVM crash before it finished. Root cause = the native
  crash. Correlate: for a handful of leftover `.mp3` ULIDs, pull that session's
  logs (`session-id` filter) and check for a restart right after.

Working hypothesis from the baseline (10 leftover `.mp3`, ~2.3 restarts/day,
hung-upload signal historically 0): **most losses are crash-induced.** Confirm
or refute this each run.

## When a crash dump lands (`/opt/pawa/data/dumps/hs_err_pid*.log`)

1. Copy it locally: `scp pawa.im:/opt/pawa/data/dumps/hs_err_pid*.log <scratch>/`.
2. Read the **Problematic frame** (expect `C  [...]  opus_decoder_get_nb_samples`),
   the **faulting thread** stack, and any SSRC/decoder context. Confirm it's the
   incoming-voice opus decode path (use-after-free during voice teardown), not
   something new.
3. Capture the JDA/jdave versions from the header and the surrounding gateway
   state.
4. This is high-value upstream evidence — JDA #2998 only has Java-level errors.
   Draft (for the human to post) a focused report / new issue with the native
   stack. Do **not** post on the user's behalf without explicit approval.

## Path to the goal

Root cause is upstream and unfixable by us in the short term (latest versions,
DAVE mandatory). So two tracks run in parallel:

- **Track A — reduce the cause:** fewer crashes = fewer losses. Keep the guild
  count down (post-purge restart lowers shard count → less concurrent voice →
  fewer chances to hit the >2-user DAVE bug). Watch the Crashes & Restarts board
  to see if this is working. Feed `hs_err` evidence upstream.

- **Track B — make losses self-heal (this is what actually hits the goal):**
  build the **Background auto-recovery** feature (see `../TODO.md`). A worker
  scans `/app/data/recordings/` for leftover `.mp3`/`.queue` (or `Recording`
  rows that were saved but have no `url`), re-runs `pawa.recoverRecording`, and
  notifies the original channel — no human, no `/recover`. This delivers "no one
  needs to recover" **even while crashes continue**, which is why it's the
  reliable path. Today recovery is manual and owner-gated (`Recover.kt:27`,
  restricted to `TRIGOMAN`), which is exactly why users still ask.

Recommend: pursue Track B to reach the goal; keep Track A's evidence-gathering
going to eventually kill the root cause.

## Known separate issues (don't conflate with the goal)

- **Queue-file disk leak:** `.queue` files are never deleted (cleanup only closes
  them) — ~1,247 and growing. Not a delivery failure, but unbounded disk growth.
  Worth a separate fix (delete the `.queue` after a successful upload, keeping it
  only when the recording is unrecovered).
- **DB `url IS NULL` is not a failure metric** — it's ~285k, dominated by
  recordings stopped without saving. Use the leftover-`.mp3` count instead.

## Exit criteria

- Leftover `.mp3` count trends to ~0 and stays there.
- Support server stops receiving recovery requests.
- Bonus / true fix: `hs_err` captured, root cause confirmed, upstream fix or
  mitigation in place so crashes stop.
