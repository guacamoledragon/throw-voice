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
