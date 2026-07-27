# Task: Fix `/save-destination` throwing "Language: EN was not found!"

Work in this repo (pawa, a Kotlin Discord voice-recording bot, Maven build) on a
new branch off master: `fix-save-destination-translator`.

## The bug

`java.lang.IllegalArgumentException: Language: EN was not found!` is the single
most frequent error in production — **239 occurrences in the 21 days to
2026-07-26**, roughly 11/day, plus 4x `Language: ID` and 2x `Language: PT_BR`.
It is logged as `Uncaught exception in event listener` by
`dev.minn.jda.ktx.events.CoroutineEventManager`, meaning the slash command dies
and the user gets a failed interaction.

Production stack trace (2026-07-26):

```
java.lang.IllegalArgumentException: Language: EN was not found!
    at tech.gdragon.commands.settings.SaveDestination$slashHandler$1.invokeSuspend(SaveDestination.kt:119)
    at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:34)
    ...
```

The throw site is `src/main/kotlin/tech/gdragon/i18n/Lang.kt:111`:

```kotlin
inline fun <reified T> commandTranslator(lang: Lang): T {
  return when (T::class) {
    AutoRecord::class -> autorecord(lang) as T
    ...
    else -> throw IllegalArgumentException("Language: $lang was not found!")
  }
}
```

**The message is misleading.** The `else` branch fires when `T::class` — the
requested *translator type* — has no branch in the `when`, not when the language
is invalid. `EN` is a perfectly valid `Lang`. So `/save-destination` is asking
for a translator type that `commandTranslator` does not handle, and every
invocation fails for every user regardless of language.

## What to do

1. Find which translator type `SaveDestination` requests and why it is missing
   from the `when`. Add the branch (or change the call site to a translator that
   exists — pick whichever is the smaller correct change).
2. Fix the exception message so it names the actual problem, e.g.
   `"No translator for ${T::class.simpleName} (lang: $lang)"`. The current
   wording sent this investigation down the wrong path once already.

Do not restructure the i18n layer, do not introduce a registry or a factory.
Add the missing branch and fix the message.

## Discrepancy to resolve first

The production stack says `SaveDestination.kt:119`, but master's
`src/main/kotlin/tech/gdragon/commands/settings/SaveDestination.kt` is **58
lines**, and it is 58 lines at the deployed commit (`8b1c9af`) too. Confirm what
the deployed build actually contains before assuming the line number maps to
current source — the running image may predate master for this file, or the
class may be generated/relocated. Report what you find. If the bug is already
gone on master, say so with evidence and stop rather than inventing a fix.

## Before you start

This brief is a problem statement, not a plan. Start with
`superpowers:brainstorming`, then `superpowers:writing-plans`, and produce the
plan before touching code. Resolve the discrepancy above during planning — it
may change the whole task.

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

1. A test that calls `commandTranslator` for the type `SaveDestination` requests
   and asserts it resolves instead of throwing. Fail -> pass evidence required.
2. Full suite: `mvn test`. `PawaTest` needs Docker for testcontainers; if Docker
   is unavailable, run the rest and flag `PawaTest` as not run.

## Report must contain

The resolution of the line-number discrepancy, fail -> pass test evidence (real
trimmed output), full-suite outcome, and anything you could not verify.

Commit with an imperative-mood message. Leave the branch unpushed and unmerged.
