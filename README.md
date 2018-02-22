# throw-voice
> A voice channel recording bot for Discord.

<p align="center">
  <a href="https://discordbots.org/bot/338897906524225538">
    <img src="https://discordbots.org/api/widget/338897906524225538.png" alt="Discord Bots" />
  </a>
</p>

[![Build Status](https://travis-ci.org/guacamoledragon/throw-voice.svg?branch=master)](https://travis-ci.org/guacamoledragon/throw-voice)
[![Coverage Status](https://coveralls.io/repos/github/guacamoledragon/throw-voice/badge.svg)](https://coveralls.io/github/guacamoledragon/throw-voice)
[![Waffle.io - Columns and their card count](https://badge.waffle.io/guacamoledragon/throw-voice.svg?columns=all)](https://waffle.io/guacamoledragon/throw-voice)
[![Docker Pulls](https://img.shields.io/docker/pulls/gdragon/throw-voice.svg)](https://hub.docker.com/r/gdragon/throw-voice/)
[![](https://images.microbadger.com/badges/version/gdragon/throw-voice.svg)](https://microbadger.com/images/gdragon/throw-voice "Get your own version badge on microbadger.com")

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
# Contents

- [Commands](#commands)
- [Self-hosting](#self-hosting)
- [Attributions](#attributions)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## Commands

|                            Command                            |                                                                  Description                                                                   |
|:--------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------|
| `!alerts [on \| off]`                                         | Turns on/off direct message alerts for when you are being recorded in a voice channel (on by default)                                          |
| `!alias [command name] [new command alias]`                   | Creates an alias, or alternate name, to a command for customization.                                                                           |
| `!autojoin [Voice Channel name \| 'all'] [number \| 'off']`   | Sets the number of players for the bot to auto-join a voice channel, or disables auto-joining. 'all' will apply number to all voice channels.  |
| `!autoleave [Voice Channel name \| 'all'] [number]`           | Sets the number of players for the bot to auto-leave a voice channel, or disables auto-leaving. 'all' will apply number to all voice channels. |
| `!autosave`                                                   | Toggles the option to automatically save and send all files at the end of each session - not just saved or clipped files                       |
| `!clip [seconds] \| !clip [seconds] [text channel output]`    | Saves a clip of the specified length and outputs it in the current or specified text channel (max 120 seconds)                                 |
| ~~`!echo [seconds]`~~                                         | ~~Echos back the input number of seconds of the recording into the voice channel (max 120 seconds)~~                                           |
| `!join`                                                       | Aliases: `record`. Force the bot to join and record your current channel                                                                       |
| `!leave`                                                      | Aliases: `stop`. Force the bot to leave it's current channel                                                                                   |
| ~~`!miab [seconds] [voice channel]`~~                         |~~Echos back the input number of seconds of the recording into the voice channel specified and then rejoins original channel (max 120 seconds)~~|
| `!prefix [character]`                                         | Aliases: `symbol`. Sets the prefix for each command to avoid conflict with other bots _(Default is '!')_                                       |
| `!removeAlias [alias name]`                                   | Removes an alias from a command.                                                                                                               |
| `!save \| !save [text channel output]`                        | Saves the current recording and outputs it to the current or specified text chats (caps at 16MB)                                               |
| `!saveLocation \| !saveLocation [text channel name]`          | Sets the text channel of message or the text channel specified as the default location to send files                                           |
| `!volume [1-100]`                                             | Sets the percentage volume to record at, from 1-100%                                                                                           |

_Replace brackets [] with item specified. Vertical bar | means 'or', either side of bar is valid choice._

## Self-hosting

Self-hosting instructions got a lot more complex, see the [self-hosting](./docs/self-hosting.md) section for more on how
you can deploy the bot.


## Attributions

- "Dragon" by [lastspark](https://thenounproject.com/lastspark) from [the Noun Project](http://thenounproject.com/).
- Bot inspired by [ajm1996's](https://github.com/ajm1996) [DiscordEcho](https://github.com/ajm1996/DiscordEcho).
