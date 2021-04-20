# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project **DOES NOT** adhere to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

### Changed
- [Pawa Lite]: Will not leave voice channel when there's no voice activity.

### Deprecated

### Removed

### Fixed

### Security

## [2.9.4]

### Changed
- [Pawa Lite]: Will not leave voice channel when there's no voice activity.

## [2.9.3]

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

[Unreleased]: https://gitlab.com/pawabot/pawa/-/compare/v2.9.4...master
[2.9.4]: https://gitlab.com/pawabot/pawa/-/compare/v2.9.3...v2.9.4
[2.9.3]: https://gitlab.com/pawabot/pawa/-/compare/v2.9.2...v2.9.3
[2.9.2]: https://gitlab.com/pawabot/pawa/-/compare/v2.9.1...v2.9.2
[2.9.1]: https://gitlab.com/pawabot/pawa/-/compare/98653c...v2.9.1
[2.9.0]: https://gitlab.com/pawabot/pawa/-/compare/5c097e...98653c
[Pawa Lite]: https://lite.pawa.im
