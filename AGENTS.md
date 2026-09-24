# Agents

## Cutting a Release

To bump the version (e.g. `v2.16.0` → `v2.17.0`), update the version number in the following files:

| File | Location |
|------|----------|
| `pom.xml` | `<version>` tag |
| `.gitlab-ci.yml` | `BOT_VERSION` variable |
| `justfile` | `--cache-from` Docker image tag |
| `launch4j.xml` | `<jar>` and `<outfile>` paths |

Then update `CHANGELOG.md`:

1. Replace the `## [Unreleased]` heading with `## [X.Y.Z] - YYYY-MM-DD` and fill in the changes.
2. Add a new empty `## [Unreleased]` section with placeholder headers (`Added`, `Changed`, `Deprecated`, `Fixed`, `Security`) above the new version section.
3. Add a footer link for the new version and update the `[Unreleased]` link:
   ```
   [Unreleased]: https://gitlab.com/pawabot/pawa/-/compare/vX.Y.Z...master
   [X.Y.Z]: https://gitlab.com/pawabot/pawa/-/compare/vX-1.Y-1.Z-1...vX.Y.Z
   ```

Commit all changes with the message `Release vX.Y.Z`, then create a tag:

```sh
git tag vX.Y.Z
```

Finally, push atomically so the branch and tag are published together:

```sh
git push --atomic origin master vX.Y.Z
```

## Testing a Change Before Release

A test stack runs in Coolify at `https://lab.rmnhb.com` (service `pawa-test`), separate
from production: its own Discord bot app, Postgres, and MinIO bucket.

It builds the bot from source at a git ref instead of pulling a released image:

1. Push the branch to GitLab.
2. In Coolify → `pawa-test` → Environment Variables, set `PAWA_REF` to the
   branch, tag, or full 40-char commit SHA (short SHAs fail).
3. Redeploy. `sql-fetch` checks out the same ref, so Flyway applies that
   branch's migrations before the bot starts.
4. Healthy start: the bot log shows `Creating Remote Database Module` and an
   `Invite URL:` line.

Migrations are not rolled back when switching to an older ref.

## Writing and Reviewing Code

- Do not write code comments that explain why a change was made or that point to an
  issue (`see #86`). Put the reason in the commit message and the MR description.
  Comments go stale.
- Do not keep a test that checks one small condition and needs a lot of setup (an
  embedded database, many mocks). Use it during development, then delete it. Tests are
  code, and we must maintain them.
- Do not keep an `if` branch that only writes a log. Negate the condition and keep one
  branch.
- Keep the MR description the same as the pushed code. If a change removes a test or a
  log, update the description.
- Stage files by name. Do not use `git commit -a`, because the working tree can have
  local changes that are not part of the MR.
- "Feedback on MR!N" means change the code on the branch. Do not post comments on the
  MR unless the user asks for them.

## Kotlin Language Server (KLS) Setup

KLS requires special setup because:

1. **Java 25 crash** — KLS 1.3.13 (and earlier) ships a vendored IntelliJ library whose `JavaVersion.parse()` cannot parse the `"25.0.x"` version string, causing an immediate crash. KLS must be run under Java 21.

2. **Kotlin 2.3.0 incompatibility** — KLS 1.3.13 bundles Kotlin compiler 2.1.0, which cannot read metadata from Kotlin 2.3.0 artifacts (this project's version), causing `INCOMPATIBLE_CLASS` errors on all stdlib symbols.

### Solution

A custom build of KLS is maintained at `~/.local/share/kotlin-language-server-src` with two changes from upstream:

- `gradle/libs.versions.toml`: `kotlinVersion = "2.3.0"` (was `2.1.0`)
- `gradle.properties`: `javaVersion=21` (was `11`)
- Three source fixes in `Compiler.kt` and `ConvertDiagnostic.kt` to handle breaking Kotlin 2.3.0 compiler API changes

To rebuild after a Kotlin version bump in `pom.xml`:

```sh
cd ~/.local/share/kotlin-language-server-src
# update gradle/libs.versions.toml: kotlinVersion = "<new version>"
JAVA_HOME="$(mise where java@temurin-21.0.10+7.0.LTS)" ./gradlew :server:installDist
```

The wrapper script `~/.bin/kotlin-language-server-lsp` runs KLS under Java 21 JDK (required both to avoid the `JavaVersion.parse` crash and for ktfmt formatting which needs `com.sun.source` from the JDK). Emacs is configured to use this wrapper via:

```emacs-lisp
(after! lsp-kotlin
  (setq lsp-kotlin--language-server-path (executable-find "kotlin-language-server-lsp"))
  (setq lsp-kotlin-compiler-jvm-target "25"))
```

## Honeycomb MCP Usage

Team `gdragon-d9`, environment `prod`, dataset `pawa`. Board links, SSH access and the
periodic checks are in `.agent/runbooks/stability-runbook.md`.

- Call `get_workspace_context` first, to confirm the environments and the datasets.
- Call `find_columns` or `get_dataset_columns` before a query. Do not guess a column name.
- Name the environment and the dataset in every query.
- Use a human-readable time range such as `-7d`. Do not use epoch timestamps.

### How data reaches the dataset

Each row in `pawa` is a log event, not a trace. `docker-compose.yml` sets
`OTEL_JAVAAGENT_ENABLED=false`, so `@WithSpan` and OTEL metrics reach nothing.
`log4j2-prod.xml` writes through `EcsLayout.json`, which flattens the MDC into the JSON root
and stringifies every value.

A key that `withLoggingContext` sets therefore becomes a **string** column: `session-id`,
`guild`, `audio.frames.dropped`, `audio.mp3.tail.shortfall`. Aggregate with `COUNT` and
`GROUP BY`. For arithmetic, add a calculated field such as `INT($audio.mp3.tail.shortfall)`.
The dataset already uses that pattern for `recording.size`.

To add a new queryable number, pass it through `withLoggingContext` with a stable key. The key
names are a query contract, because boards and triggers read them.
