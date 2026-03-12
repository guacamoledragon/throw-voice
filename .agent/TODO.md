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

## Cleanup: Remove `src/assembly` directory

The `src/assembly` directory (docker-compose, `.env`) is out of date and no longer reflects the current deployment setup. Delete it entirely.
