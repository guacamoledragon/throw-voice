# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project **DOES NOT** adhere to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.13.0] - 2024-04-14

### Added
- Show accurate duration of Recording
  - Only storing this in the memory data model, will need to create migration to store in Database
- Add OTEL instrumentation
  - Trying out SigNoz Cloud, currently sending app and db metrics as well as logs
- `<prefix>test` command to simulate `@pawa` performing certain actions like joining a VC then saving.

### Changed
- `recover` responds with ephemeral replies only visible to caller
- Update the following dependencies:
  - Bump aws.sdk.kotlin:s3-jvm from 1.0.48 to 1.1.13
  - Bump com.fasterxml.jackson.core:jackson-databind from 2.16.0 to 2.17.0
  - Bump commons-io:commons-io from 2.15.1 to 2.16.0
  - Bump exposed.version from 0.45.0 to 0.47.0
  - Bump io.github.oshai:kotlin-logging-jvm from 5.1.0 to 6.0.9
  - Bump io.opentelemetry:opentelemetry-bom from 1.34.0 to 1.37.0
  - Bump jda.version 5.0.0-beta.20 to 5.0.0-beta.22
  - Bump kotest.version from 5.8.0 to 5.8.1
  - Bump kotlin.version from 1.9.22 to 1.9.23
  - Bump nrepl:nrepl from 1.1.0 to 1.1.1
  - Bump org.clojure:clojure from 1.11.1 to 1.12.0-alpha9
  - Bump org.flywaydb:flyway-core from 9.22.1 to 10.11.0
    - Required adding org.flywaydb:flyway-database-postgesql runtime dependency
  - Bump org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm 1.7.3 to 1.8.0
  - Bump org.postgresql:postgresql from 42.7.1 to 42.7.2
  - Bump org.testcontainers:postgresql from 1.19.4 to 1.19.7
  - Bump org.testcontainers:testcontainers from 1.19.3 to 1.19.7
  - Bump otel.version 2.2.0 to 2.3.0

### Removed
- Remove EventTracer, was remnant from removing Honeycomb

### Fixed
- [PawaLite]: `/recover` now correctly recovers a recording from a queue file

## [2.12.0] - 2024-02-13

### Added
- `Recover Recording` message context menu to recover recording from a SessionID in a message
- [PawaLite]: `/autorecord` slash command added


### Changed
- `/record` displays an embed, this will replace the original message eventually
- `/recover` run by normal user will send the SessionID and Discord username, the response will also include a server invite.
- [PawaLite]: `/recover` command enabled
- [PawaLite]: `/autosave` on by default
- Increase recording limit from 110MB -> 256MB
- pawa will respond in Voice Channel chat if it cannot detect an origin text channel
- Update the following dependencies:
  - Bump aws.sdk.kotlin:s3-jvm from 0.27.0-beta to 1.0.48
  - Bump caffeine from 3.1.1 to 3.1.6
  - Bump com.fasterxml.jackson.core:jackson-databind from 2.15.1 to 2.16.0
  - Bump com.github.ben-manes.caffeine:caffeine from 3.1.6 to 3.1.8
  - Bump com.github.minndevelopment:jda-ktx from 0.10.0-beta.1 to 0.11.0-beta.19
  - Bump commons-io:commons-io from 2.11.0 to 2.15.1
  - Bump exposed.version from 0.39.2 to 0.45.0
  - Bump flyway-core from 9.14.1 to 9.16.3
  - Bump io.insert-koin:koin-core from 3.4.0 to 3.5.0
  - Bump jackson-databind from 2.14.2 to 2.15.1
  - Bump jda.version 5.0.0-beta.9 to 5.0.0-beta.20
  - Bump koin-core from 3.2.0 to 3.4.0
  - Bump koin-core-jvm from 3.2.0 to 3.4.0
  - Bump koin.version from 3.5.0 to 3.5.3
  - Bump kotlin.version from 1.8.10 to 5.8.0
  - Bump log4j-core from 2.18.0 to 2.20.0
  - Bump log4j-layout-template-json from 2.18.0 to 2.20.0
  - Bump log4j.version from 2.20.0 to 2.22.1
  - Bump maven-assembly-plugin from 3.4.2 to 3.11.0
  - Bump maven-resources-plugin from 3.3.0 to 3.3.1
  - Bump maven-surefire-plugin from 3.0.0 to 3.1.0
  - Bump microutils:kotlin-logging-jvm from 3.0.5 to 5.1.0
  - Bump nrepl:nrepl from 1.0.0 to 1.1.0
  - Bump org.apache.maven.plugins:maven-compiler-plugin
  - Bump org.apache.maven.plugins:maven-shade-plugin from 3.4.1 to 3.5.1
  - Bump org.apache.maven.plugins:maven-surefire-plugin from 3.1.0 to 3.2.5
  - Bump org.flywaydb:flyway-core from 9.16.3 to 9.22.1
  - Bump org.postgresql:postgresql from 42.6.0 to 42.7.1
  - Bump org.testcontainers:postgresql from 1.18.0 to 1.19.4
  - Bump org.testcontainers:testcontainers from 1.18.3 to 1.19.3
  - Bump postgresql from 1.17.6 to 42.6.0
  - Bump slf4j-api from 2.0.0-alpha2 to 2.0.9
  - Bump testcontainers from 1.17.6 to 1.18.3
  - Bump ulidj from 1.0.1 to 1.0.4


### Deprecated
- `RemoteDatastore` class in exchange for a generic S3 API compatible `S3Datastore`

### Removed
- `minio` pom dependency
- `minio` from docker-compose.yml
- Honeycomb metrics and the `Honey` tracing class
  - Remove `honeycomb-opentelemetry-sdk`


### Fixed
- Errors when sending slash commands from the Voice Channel chat
- `/autorecord` correctly follows member when switching between Voice Channels


### Security
- Bump com.h2database:h2 from 2.1.214 to 2.2.224
  - Added `Upgrader.kt` to automatically upgrade H2 DB for PawaLite users

## [2.11.1] - 2023-03-01

### Added
- Added `/recover` command
  - This command can help us restore recordings that failed to upload with a Session ID.
- Database migration tests
  - Not a feature, but will let us make changes to the database more often.
- Add testcontainers dependencies, this makes it straightforward to run database tests

### Changed
- Update JDA 4 -> [JDA 5 Beta 5](https://github.com/DV8FromTheWorld/JDA/releases/tag/v5.0.0-beta.5)
  - This was massive change, and will be testing it before updating [PawaLite]
- Update [jda-ktx](https://github.com/MinnDevelopment/jda-ktx/releases/tag/0.10.0-beta.1)
- Update the following dependencies:
  - Bump flyway-core 9.3.0 -> 9.14.1
  - Bump jda.ktx -> 0.10.0.beta.1
  - Bump jda.version 4.4.0_352 -> 5.0.0.beta.5
  - Bump kotest.version 5.4.2 -> 5.5.4
  - Bump kotlin.version 1.7.10 -> 1.8.10
  - Bump maven-surefire-plugin 2.22.2 -> 3.0.0-M8

### Removed
- `clip` command is now gone from the codebase
  - This command was supposed to extract a short section from the current recording, but I could never get the
    implementation quite right, so decided to remove it.

### Fixed
- One 2022-03-01 Discord made a change to the Voice handshake protocol which broke the audio recording feature.
  More details on the [JDA release](https://github.com/DV8FromTheWorld/JDA/releases/tag/v5.0.0-beta.5)

## [2.11.0] - 2022-12-23

### Added
- `/volume` command that works exactly like `!volume`
- `/save` command that works exactly like `!save`, except that the slash command must reply so it
  sends a check mark ✔️ 
- `/stop` command that works exactly like `!stop`
- `/record` command that works exactly like `!record`
- `/lang` command that works exactly like `!lang`
- `/ignore` command that works exactly like `!ignore`
  - Except that you can only ignore one user at time, must run command multiple times
- `/autosave` command that works exactly like `!autosave`
- `/autostop` command that works exactly like `!autostop`
  - Except that the setting can only be changed for one channel at a time
- `/alias` command that works exactly like `!alias`
- API layer via the `Pawa` class
  - This should make it straightforward to add new functionality that can be tested
- `kotest` testing library

### Changed
- [PawaLite] slash commands are now automatically registered upon start
- Slash commands don't seem to respect permissions, this may be an issue in the current version of
  JDA, will be updating to the latest version in the coming weeks
- `autostop` can now accept 0 as the threshold which is equivalent to `off`

### Fixed
- #44 Recording would get lost if channel didn't have MESSAGE_ATTACH_FILES permissions,
  now a recording won't start if this permission is missing.
- #54 No more double AutoStop message

## [2.10.0] - 2022-07-08

### Added
- `!slash` command to manage Slash commands, this is only available to use by me and [PawaLite] users
  - Provides an invitation URL for ease of access
  - Removes, Adds, and Lists all Slash commands, currently only `/info` added in [2.9.7]
- Translations for Filipino 🇵🇭
- Translations for Polish 🇵🇱
- Translations for Hungarian 🇭🇺

### Changed
- Update Java 11 LTS to 17 LTS
- [PawaLite] now supports `!status` command.
- Update the following dependencies:
  - Bump flyway-core from 8.3.0 to 8.5.13
  - Bump h2 from 1.4.200 to 2.1.214
  - Bump honeycomb-opentelemetry-sdk from 1.1.1 to 1.2.0
  - Bump koin-core from 3.1.4 to 3.2.0
  - Bump koin-core-jvm from 3.1.4 to 3.2.0
  - Bump kotlin-logging-jvm from 2.1.21 to 2.1.23
  - Bump kotlin.version from 1.6.10 to 1.7.10
  - Bump libhoney-java from 1.4.0 to 1.5.0
  - Bump log4j-core from 2.17.0 to 2.18.0
  - Bump log4j-layout-template-json from 2.17.2 to 2.18.0
  - Bump log4j-slf4j18-impl from 2.17.0 to 2.18.0
  - Bump maven-assembly-plugin from 3.3.0 to 3.4.1
  - Bump maven-compiler-plugin from 3.8.1 to 3.10.0
  - Bump maven-shade-plugin from 3.2.4 to 3.3.0
  - Bump minio from 8.3.4 to 8.4.2
  - Bump postgresql from 42.3.1 to 42.4.0

### Deprecated
- [PawaLite] cannot upgrade in place for versions after 2.9.8, see User Guide

### Fixed
- If there's an issue creating an MP3 file from a queue file, the queue file would get deleted making it impossible to
  recover. This is a hack, but it'll work for now...

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
- [PawaLite]: Omit 24 hour warning
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
- [PawaLite]: Some commands had issues with the H2 embedded database transactions, so had to adjust code to cache the
  objects and prevent re-query'ing for them after the database connection had closed.

## [2.9.5] - 2021-06-11

### Added
- Add new command `!lang`, provides a way to change the bot's language
  - Create a system for adding new languages in the future
- [PawaLite]: Apply database migrations on startup

### Changed
- Switch from using UUIDs to ULIDs

### Fixed
- [PawaLite]: Include binaries necessary for decoding OPUS
- Adding an alias twice in a row would result in an error and would break the alias

## [2.9.4] - 2021-04-19

### Changed
- [PawaLite]: Will not leave voice channel when there's no voice activity.

## [2.9.3] - 2021-03-28

### Added
- [PawaLite]: `autorecord` command now works!
- `status` command
  Mainly for debugging purposes and only accessible to me, this shows the status of the shards
- `ignore` command
  Removes other bots from recording session
- Add LibHoney dependency to track events, doesn't contain any user or guild data
  No tracking for [PawaLite]

### Changed
- Show warning when attempting to use `autorecord`
- [PawaLite]: `record` command now also takes an argument that allows caller to record a voice
  channel without being in it

### Fixed
- Open Graph meta tags now display proper information for links

## [2.9.2] - 2021-03-01

### Added
- Display a warning about saving recording
- Add Caching library to cache database requests for Guild prefix

### Changed
- [PawaLite]: Recordings are uploaded using the following format
  `<voice-channel>-yyyyMMdd'T'HHmmss.SSSZ` e.g. `bot-testing-20210302T182001.176-0800`

## [2.9.1] - 2021-01-29
### Changed
- [PawaLite]: Replace Embedded Postgres with H2
- This drastically reduces the package size (about 20MB)
- [PawaLite]: Keep `queue` files, might create congestion, but will make it easier to recover if needed

### Fixed
- [PawaLite]: No longer needed to exit with Ctrl-C

## [2.9.0] - 2021-01-18
### Added
- PawaLite configuration

### Fixed
- Remove recording limits and warnings

[2.13.0]: https://gitlab.com/pawabot/pawa/-/compare/v2.12.0...v2.13.0
[2.12.0]: https://gitlab.com/pawabot/pawa/-/compare/v2.11.1...v2.12.0
[2.11.1]: https://gitlab.com/pawabot/pawa/-/compare/v2.11.0...v2.11.1
[2.11.0]: https://gitlab.com/pawabot/pawa/-/compare/v2.11.0...v2.10.0
[2.10.0]: https://gitlab.com/pawabot/pawa/-/compare/v2.9.8...v2.10.0
[2.9.8]: https://gitlab.com/pawabot/pawa/-/compare/v2.9.7...v2.9.8
[2.9.7]: https://gitlab.com/pawabot/pawa/-/compare/v2.9.6...v2.9.7
[2.9.6]: https://gitlab.com/pawabot/pawa/-/compare/v2.9.5...v2.9.6
[2.9.5]: https://gitlab.com/pawabot/pawa/-/compare/v2.9.4...v2.9.5
[2.9.4]: https://gitlab.com/pawabot/pawa/-/compare/v2.9.3...v2.9.4
[2.9.3]: https://gitlab.com/pawabot/pawa/-/compare/v2.9.2...v2.9.3
[2.9.2]: https://gitlab.com/pawabot/pawa/-/compare/v2.9.1...v2.9.2
[2.9.1]: https://gitlab.com/pawabot/pawa/-/compare/98653c...v2.9.1
[2.9.0]: https://gitlab.com/pawabot/pawa/-/compare/5c097e...98653c
[PawaLite]: https://lite.pawa.im
