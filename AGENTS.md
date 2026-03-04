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
