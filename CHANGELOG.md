# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project **DOES NOT** adhere to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `!slash` command to manage Slash commands, this is only available to use by me and [Pawa Lite] users
  - Provides an invitation URL for ease of access
  - Removes, Adds, and Lists all Slash commands, currently only `/info` added in [2.9.7]
- Translations for Filipino 🇵🇭
- Translations for Polish 🇵🇱

### Changed
- [Pawa Lite] now supports `!status` command.
- Update the following dependencies:
  - Bump flyway-core from 8.3.0 to 8.5.1
  - Bump h2 from 1.4.200 to 2.1.210
  - Bump koin-core from 3.1.4 to 3.1.5
  - Bump koin-core-jvm from 3.1.4 to 3.1.5
  - Bump libhoney-java from 1.4.0 to 1.4.1
  - Bump log4j-core from 2.17.0 to 2.17.2
  - Bump log4j-slf4j18-impl from 2.17.0 to 2.17.2
  - Bump maven-compiler-plugin from 3.8.1 to 3.10.0
  - Bump minio from 8.3.4 to 8.3.7
  - Bump postgresql from 42.3.1 to 42.3.3

### Deprecated
- [Pawa Lite] cannot upgrade in place for versions after 2.9.8, see User Guide

### Removed

### Fixed
- If there's an issue creating an MP3 file from a queue file, the queue file would get deleted making it impossible to
  recover. This is a hack, but it'll work for now...

### Security

## [2.9.8] - 2021-12-25

### Added
- Translations for Vietnamese 🇻🇳

### Changed
- Update the following dependencies:
  - Bump JDA from 4.3.0_346 to 4.4.0_352
  - Bump caffeine from 3.0.4 to 3.0.5
  - Bump flyway-core from 8.0.5 to 8.2.2
  - Bump flyway-core from 8.2.1 to 8.3.0
  - Bump jackson-databind from 2.13.0 to 2.13.1
  - Bump koin-core from 3.1.3 to 3.1.4
  - Bump koin-core-jvm from 3.1.3 to 3.1.4
  - Bump kotlin-logging-jvm from 2.1.0 to 2.1.20
  - Bump kotlin-logging-jvm from 2.1.20 to 2.1.21
  - Bump kotlin.version from 1.6.0 to 1.6.10
  - Bump libhoney-java from 1.3.1 to 1.4.0
  - Bump log4j-slf4j18-impl from 2.14.1 to 2.16.0
  - Bump log4j-slf4j18-impl from 2.16.0 to 2.17.0
  - Bump maven-shade-plugin.log4j2-cachefile-transformer from 2.14.1 to 2.15
  - Bump nrepl from 0.8.3 to 0.9.0

### Removed
- Remove NanoHTTPD
  - No longer in use

### Security
- Fix [Log4Shell](https://nvd.nist.gov/vuln/detail/CVE-2021-44228)
  - Update log4j-core 2.14.1 -> 2.17.0

## [2.9.7] - 2021-11-19

### Added
- Translations for:
  - Italian
  - German
  - Portuguese
  - Thai (partial)
- Slash command `/info` to display information about Guild

### Changed
- REPL class implementation, matlux/jvm-breakglass -> nrepl/nrepl
- [Pawa Lite]: Omit 24 hour warning
- Update the following dependencies:
  - JDA from 4.3.0_324 to 4.3.0_346
  - exposed from 0.17.13 to 0.17.14
  - failsafe from 2.4.3 to 2.4.4
  - flyway-core from 7.15.0 to 8.0.5
  - jackson-databind from 2.12.5 to 2.13.0
  - jda-ktx from ea0a1b2 to 1223d5c
  - koin-core from 2.2.2 to 3.1.3
  - kotlin-logging-jvm from 2.0.11 to 2.1.0 
  - kotlin.version from 1.5.30 to 1.6.0
  - postgresql from 42.2.23 to 42.3.1

### Fixed
- Error about logging on startup
  - Any version past 2.0.0-alpha2 is broken for us

## [2.9.6] - 2021-06-29

### Added
- Add beta support for [Stage Channels](https://github.com/DV8FromTheWorld/JDA/pull/1575)
- Add translations for:
  - Indonesian
  - Spanish (MX)
  - French

### Changed
- Change activity from "Playing" to "Listening"

### Fixed
- [Pawa Lite]: Some commands had issues with the H2 embedded database transactions, so had to adjust code to cache the
  objects and prevent re-query'ing for them after the database connection had closed.

## [2.9.5] - 2021-06-11

### Added
- Add new command `!lang`, provides a way to change the bot's language
  - Create a system for adding new languages in the future
- [Pawa Lite]: Apply database migrations on startup

### Changed
- Switch from using UUIDs to ULIDs

### Fixed
- [Pawa Lite]: Include binaries necessary for decoding OPUS
- Adding an alias twice in a row would result in an error and would break the alias

## [2.9.4] - 2021-04-19

### Changed
- [Pawa Lite]: Will not leave voice channel when there's no voice activity.

## [2.9.3] - 2021-03-28

### Added
- [Pawa Lite]: `autorecord` command now works!
- `status` command
  Mainly for debugging purposes and only accessible to me, this shows the status of the shards
- `ignore` command
  Removes other bots from recording session
- Add LibHoney dependency to track events, doesn't contain any user or guild data
  No tracking for [Pawa Lite]

### Changed
- Show warning when attempting to use `autorecord`
- [Pawa Lite]: `record` command now also takes an argument that allows caller to record a voice
  channel without being in it

### Fixed
- Open Graph meta tags now display proper information for links

## [2.9.2] - 2021-03-01

### Added
- Display a warning about saving recording
- Add Caching library to cache database requests for Guild prefix

### Changed
- [Pawa Lite]: Recordings are uploaded using the following format
  `<voice-channel>-yyyyMMdd'T'HHmmss.SSSZ` e.g. `bot-testing-20210302T182001.176-0800`

## [2.9.1] - 2021-01-29
### Changed
- [Pawa Lite]: Replace Embedded Postgres with H2
- This drastically reduces the package size (about 20MB)
- [Pawa Lite]: Keep `queue` files, might create congestion, but will make it easier to recover if needed

### Fixed
- [Pawa Lite]: No longer needed to exit with Ctrl-C

## [2.9.0] - 2021-01-18
### Added
- Pawa Lite configuration

### Fixed
- Remove recording limits and warnings

[2.9.8]: https://gitlab.com/pawabot/pawa/-/compare/v2.9.7...v2.9.8
[2.9.7]: https://gitlab.com/pawabot/pawa/-/compare/v2.9.6...v2.9.7
[2.9.6]: https://gitlab.com/pawabot/pawa/-/compare/v2.9.5...v2.9.6
[2.9.5]: https://gitlab.com/pawabot/pawa/-/compare/v2.9.4...v2.9.5
[2.9.4]: https://gitlab.com/pawabot/pawa/-/compare/v2.9.3...v2.9.4
[2.9.3]: https://gitlab.com/pawabot/pawa/-/compare/v2.9.2...v2.9.3
[2.9.2]: https://gitlab.com/pawabot/pawa/-/compare/v2.9.1...v2.9.2
[2.9.1]: https://gitlab.com/pawabot/pawa/-/compare/98653c...v2.9.1
[2.9.0]: https://gitlab.com/pawabot/pawa/-/compare/5c097e...98653c
[Pawa Lite]: https://lite.pawa.im
