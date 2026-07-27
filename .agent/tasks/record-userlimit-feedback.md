# Task: `/record` fails silently when the voice channel is full

Work in this repo (pawa, a Kotlin Discord voice-recording bot, Maven build) on a
new branch off master: `fix-record-userlimit-feedback`.

## The bug

**34 occurrences in the 21 days to 2026-07-26.** A user runs `/record` on a
voice channel that is at its user limit, JDA throws, the exception escapes the
coroutine handler, and the user gets **no feedback at all** — no error message,
just a failed interaction. No recording session is ever created, so this class
of failure is invisible to every existing recording-failure metric (leftover
`.mp3`, upload errors, session logs).

Production stack trace (2026-07-26):

```
net.dv8tion.jda.api.exceptions.InsufficientPermissionException:
  Unable to connect to AudioChannel due to userlimit! Requires permission VOICE_MOVE_OTHERS to bypass
    at net.dv8tion.jda.internal.managers.AudioManagerImpl.checkChannel(AudioManagerImpl.java:114)
    at net.dv8tion.jda.internal.managers.AudioManagerImpl.openAudioConnection(AudioManagerImpl.java:89)
    at tech.gdragon.BotUtils.recordVoiceChannel(BotUtils.kt:334)
    at tech.gdragon.commands.audio.Record.handler(Record.kt:61)
    at tech.gdragon.commands.audio.Record$slashHandler$1.invokeSuspend(Record.kt:39)
    ...
```

Logged as `Uncaught exception in event listener` by
`dev.minn.jda.ktx.events.CoroutineEventManager`.

## What to do

Catch `InsufficientPermissionException` around the
`BotUtils.recordVoiceChannel` call in `Record.handler`
(`src/main/kotlin/tech/gdragon/commands/audio/Record.kt:61`) and reply to the
user with an actionable message — the channel is full, and the bot needs
`VOICE_MOVE_OTHERS` to join a full channel.

Look at how other commands in `src/main/kotlin/tech/gdragon/commands/` surface
errors to the user (there is an existing error-reply type in
`src/main/kotlin/tech/gdragon/discord/message/`) and match that pattern rather
than inventing a new one. Reuse an existing translation string if one fits;
adding a new one is fine, but do not build a new error-handling abstraction.

Note this same exception type also appears (3x in the same window) from the
upload path — that one is already handled by the datastore fallback (MR !142).
Scope this task to the `/record` join path only.

## Before you start

This brief is a problem statement, not a plan. Start with
`superpowers:brainstorming`, then `superpowers:writing-plans`, and produce the
plan before touching code. The exact wording shown to the user is a product
decision — bring options to the human rather than picking one silently.

The human who filed this is the project's main developer, is available, and
wants to be asked. Check in when: the evidence here does not match what you find
in the code, the fix looks materially bigger than this brief implies, or you are
about to change behaviour the brief does not mention. Do not guess and proceed on
any of those.

## Ground rules

- Never touch production (pawa.im, SSH, the prod DB). Local code and tests only.
  Do not deploy, tag, or edit `CHANGELOG.md`.
- Use `superpowers:test-driven-development` and
  `superpowers:verification-before-completion`.

## Required verification

1. A test that makes `recordVoiceChannel` throw `InsufficientPermissionException`
   and asserts the user receives an error reply instead of the exception escaping.
   Fail -> pass evidence required.
2. Full suite: `mvn test`. `PawaTest` needs Docker for testcontainers; if Docker
   is unavailable, run the rest and flag `PawaTest` as not run.

## Report must contain

Fail -> pass test evidence (real trimmed output), which existing error-reply
pattern you reused, full-suite outcome, and anything you could not verify.

Commit with an imperative-mood message. Leave the branch unpushed and unmerged.
