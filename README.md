# throw-voice
> An audio recording bot for Discord.

[![Build Status](https://travis-ci.org/guacamoledragon/throw-voice.svg?branch=master)](https://travis-ci.org/guacamoledragon/throw-voice)  

**Full Disclosure: This is a highly modified fork of [ajm1996/DiscordEcho](https://github.com/ajm1996/DiscordEcho).**

## Commands

|                         Command                         |                                                                  Description                                                                 |
|:-------------------------------------------------------:|:--------------------------------------------------------------------------------------------------------------------------------------------:|
| !alerts [on | off]                                       | Turns on/off direct message alerts for when you are being recorded in a voice channel (on by default)                                        |
| !alias [command name] [new command alias]               | Creates an alias, or alternate name, to a command for customization.                                                                         |
| !autojoin [Voice Channel name | 'all'] [number | 'off'] | Sets the number of players for the bot to auto-join a voice channel, or disables auto-joining. All will apply number to all voice channels.  |
| !autoleave [Voice Channel name | 'all'] [number]        | Sets the number of players for the bot to auto-leave a voice channel, or disables auto-leaving.,All will apply number to all voice channels. |
| !autosave                                               | Toggles the option to automatically save and send all files at the end of each session - not just saved or clipped files                     |
| !clip [seconds] | clip [seconds] [text channel output]  | Saves a clip of the specified length and outputs it in the current or specified text channel (max 120 seconds)                               |
| !echo [seconds]                                         | Echos back the input number of seconds of the recording into the voice channel (max 120 seconds)                                             |
| !join                                                   | Aliases: `record`. Force the bot to join and record your current channel                                                                     |
| !leave                                                  | Aliases: `stop`. Force the bot to leave it's current channel                                                                                 |
| !miab [seconds] [voice channel]                         | Echos back the input number of seconds of the recording into the voice channel specified and then rejoins original channel (max 120 seconds) |
| !prefix [character]                                     | Aliases: symbol. Sets the prefix for each command to avoid conflict with other bots (Default is '!')                                         |
| !removeAlias [alias name]                               | Removes an alias from a command.                                                                                                             |
| !save | !save [text channel output]                     | Saves the current recording and outputs it to the current or specified text chats (caps at 16MB)                                             |
| !saveLocation | !saveLocation [text channel name]       | Sets the text channel of message or the text channel specified as the default location to send files                                         |
| !volume [1-100]                                         | Sets the percentage volume to record at, from 1-100%                                                                                         |

## Attributions

- Japanese Dragon icon made by [Freepik](http://www.freepik.com) from [www.flaticon.com](http://www.flaticon.com) is licensed by [CC 3.0 BY](http://creativecommons.org/licenses/by/3.0/)
- Original codebase by [ajm1996](https://github.com/ajm1996)
