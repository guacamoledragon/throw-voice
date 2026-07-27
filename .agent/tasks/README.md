# Task briefs

One file per task. Each is self-contained — point an agent at a single file and
it has everything it needs to plan and execute without reading the others.

All four came out of the 2026-07-26 production stability review (Honeycomb env
`prod` + `pawa.im`), which ran the periodic check in
[`../runbooks/stability-runbook.md`](../runbooks/stability-runbook.md).

| Task | Problem | Priority |
|---|---|---|
| [`double-save-race.md`](double-save-race.md) | Concurrent `saveRecording` overwrites a good recording with a 0-byte S3 object. 9 sessions in 21 days, 4 confirmed destroyed. | **First** — actively losing user data |
| [`save-destination-translator.md`](save-destination-translator.md) | `/save-destination` throws `Language: EN was not found!` for every user, ~11x/day. | High |
| [`record-userlimit-feedback.md`](record-userlimit-feedback.md) | `/record` into a full voice channel throws uncaught; user gets no feedback. 34x in 21 days. | Medium |
| [`runbook-2026-07-26-update.md`](runbook-2026-07-26-update.md) | Refresh the runbook baseline, record the first captured crash dump, and correct the goal metric. Docs only. | After the race fix lands |

The three code tasks are independent — they touch different files and can run in
parallel on separate branches. The runbook task should go last, because
`double-save-race.md` is what makes the leftover-`.mp3` metric trustworthy again.

Each brief specifies its own branch name, keeps hands off production, and
requires fail -> pass test evidence plus a `mvn test` run before it can be called
done.
