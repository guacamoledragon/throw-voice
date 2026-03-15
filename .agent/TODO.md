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

## Cleanup: Remove `src/assembly` directory

The `src/assembly` directory (docker-compose, `.env`) is out of date and no longer reflects the current deployment setup. Delete it entirely.
