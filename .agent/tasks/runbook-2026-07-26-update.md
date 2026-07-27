# Task: Update the stability runbook with the 2026-07-26 baseline

Work in this repo (pawa) on a new branch off master: `runbook-2026-07-26`.

Edit `.agent/runbooks/stability-runbook.md` (and `.agent/TODO.md` where noted).
Documentation only — no code changes. Never touch production.

## Before you start

Read the current runbook end to end first; these are edits to a living document,
not a rewrite — preserve its structure, voice, and anything not contradicted
below. No brainstorming phase needed, but list the edits you intend to make
before making them.

The numbers below were measured on 2026-07-26. You have read-only Honeycomb (MCP
server `honeycomb`, team `gdragon-d9`, env `prod`, dataset `pawa`) and read-only
SSH to `pawa.im` — re-verify any figure you are about to write down, and report
any that no longer matches rather than copying it forward.

The human who filed this is the project's main developer and is available if a
finding here conflicts with what you measure.

## 1. Replace the "What's true right now" baseline

Current section is headed `## What's true right now (2026-07-06 baseline)`.
Replace it with a 2026-07-26 baseline, keeping the same structure. Deployed
state: container `registry.gitlab.com/pawabot/pawa:2.17.0-8b1c9af0` (the JDA
`Decoder` decode-after-close patch, commit `8b1c9af`), running since
2026-07-23 23:10 UTC, `RestartCount=0`.

Measurements for 2026-07-06 .. 2026-07-26 (21 days), Honeycomb env `prod`,
dataset `pawa`, plus SSH to `pawa.im`:

| Metric | 2026-06-27 | 2026-07-06 | 2026-07-26 |
|---|---|---|---|
| Bot restarts | ~2.3/day | ~1.0/day | 13 in 21d = **0.62/day**; **0 unplanned** in the 3.2 days since the decoder patch (the single `ONLINE` event in that window is the deploy itself) |
| Hung uploads (`Upload did not finish`) | 0 | 4/wk | 19 in 21d — at least one is a double-save symptom, not a real hang |
| Leftover `.mp3` | 10 | 8 | **0** — but see "metric is now blind" below |
| Leftover `.queue` (disk leak) | ~1,247 | 1,155 | 1,252 (+5/day) |
| `hs_err` dumps captured | 0 | 0 | **1** (2026-07-14) |
| `Error uploading recording` | — | — | 25 events / 24 sessions in 21d |
| Datastore fallback engaged | — | 0 (not deployed) | 17 in 21d — MR !142 is working |
| `Failed to process completed recording` | — | — | 4 in 21d |

## 2. Add the crash-dump finding

The runbook's "When a crash dump lands" section is now satisfiable — one dump
exists at `/opt/pawa/data/dumps/hs_err_pid7.log` (a renamed copy sits alongside
it). Record its content so the next run does not re-derive it:

- SIGSEGV, `si_code: 1 (SEGV_MAPERR)`, `si_addr: 0xc`, `RAX=0`
- Problematic frame: `C [jna...tmp+0x2ed58] opus_decoder_get_nb_samples+0x8`
- Faulting thread: `JDA [2 / 13] AudioConnection Guild: 999374375608406097 Receiving Thread`
- Java frames: `Decoder.decodeFromOpus` <- `AudioConnection.lambda$setupReceiveThread$0`
- `RDI` (the decoder pointer) points at reused garbage, not a valid `OpusDecoder`

This is exactly the decode-after-close race that commit `8b1c9af` patches, and
it is stronger evidence than anything on [JDA #2998](https://github.com/discord-jda/JDA/issues/2998),
which has Java-level errors only. Note in the runbook that this is worth
drafting for upstream — **for the human to post, not the agent.**

Also note: only 1 dump for 13 restarts, so most restarts are deploys/planned,
not crashes. The runbook's "restart ~= crash" assumption should be corrected.

## 3. Correct the goal metric — it is now blind

This is the most important edit. The runbook says leftover-`.mp3` trending to 0
means the goal is met. **That is no longer true.** A concurrent double-save race
(see `.agent/tasks/double-save-race.md`) overwrites a good S3 object with a
0-byte one at the same key and deletes the local file, so the recording is lost
with nothing left on disk and a working-looking `url` in the DB.

Rewrite step 1 of "Periodic check" to say leftover-`.mp3` is necessary but not
sufficient, and add two Honeycomb checks that do catch it:

- `Finished uploading file - (0 bytes)` — direct evidence of a destroyed
  recording (4 in the 21-day window)
- duplicate `Processing completed recording: <ulid>` for the same session —
  the race itself (9 sessions in the 21-day window)

## 4. Add the uncaught-listener-exception signal

`Uncaught exception in event listener` (logger
`dev.minn.jda.ktx.events.CoroutineEventManager`) is the top error in the
dataset, **324 in 21 days**, and the runbook tracks none of it. Add it to the
"Honeycomb queries" list with the breakdown by `error.type` / `error.message`:

- 239x `IllegalArgumentException: Language: EN was not found!` — `/save-destination`
  broken for everyone (`.agent/tasks/save-destination-translator.md`)
- 43x `ErrorResponseException 10062` — interaction not acknowledged within 3s
- 34x `InsufficientPermissionException ... userlimit` — `/record` fails with no
  user feedback (`.agent/tasks/record-userlimit-feedback.md`); these are
  recording failures that create no session and so appear in no existing metric

Note that `error.type`, `error.message`, and `error.stack_trace` are queryable
columns on the `pawa` dataset — the runbook never mentions them and they make
this breakdown a one-query job.

## 5. Add the DAVE MLS signal to watch

The same native library that segfaults logs, over the 21-day window:
`session.cpp:415 Failed to process MLS commit: bad_optional_access` (104),
`session.cpp:630 Cannot initialize join key package without a leaf node` (104),
`session.cpp:698 Cannot marshal an uninitialized key package` (104) — identical
counts, so one triple per event. Worth tracking as a possible leading indicator
of the voice-session teardown churn that precedes the crash. Flag it as
unconfirmed correlation, not established cause.

## 6. Update `.agent/TODO.md`

The "Background auto-recovery" item says to decide based on whether the
leftover-`.mp3` count stays at ~0 after MR !142. It is at 0 — but per section 3
that number no longer proves delivery. Update the item: the decision is blocked
on fixing the double-save race first, because until then the count cannot be
trusted as evidence either way.

## Report must contain

A diff summary of what changed in each file and anything in the numbers above
you could not independently re-verify.

Commit with an imperative-mood message. Leave the branch unpushed and unmerged.
