# throw-voice
> A voice channel recording bot for Discord.

[![Build Status](https://travis-ci.org/guacamoledragon/throw-voice.svg?branch=master)](https://travis-ci.org/guacamoledragon/throw-voice)
[![Coverage Status](https://coveralls.io/repos/github/guacamoledragon/throw-voice/badge.svg)](https://coveralls.io/github/guacamoledragon/throw-voice)

**Full Disclosure: This is a highly modified fork of [ajm1996/DiscordEcho](https://github.com/ajm1996/DiscordEcho).**

## Commands

|                            Command                            |                                                                  Description                                                                   |
|:--------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------|
| `!alerts [on \| off]`                                         | Turns on/off direct message alerts for when you are being recorded in a voice channel (on by default)                                          |
| `!alias [command name] [new command alias]`                   | Creates an alias, or alternate name, to a command for customization.                                                                           |
| `!autojoin [Voice Channel name \| 'all'] [number \| 'off']`   | Sets the number of players for the bot to auto-join a voice channel, or disables auto-joining. 'all' will apply number to all voice channels.  |
| `!autoleave [Voice Channel name \| 'all'] [number]`           | Sets the number of players for the bot to auto-leave a voice channel, or disables auto-leaving. 'all' will apply number to all voice channels. |
| `!autosave`                                                   | Toggles the option to automatically save and send all files at the end of each session - not just saved or clipped files                       |
| `!clip [seconds] \| !clip [seconds] [text channel output]`    | Saves a clip of the specified length and outputs it in the current or specified text channel (max 120 seconds)                                 |
| `!echo [seconds]`                                             | Echos back the input number of seconds of the recording into the voice channel (max 120 seconds)                                               |
| `!join`                                                       | Aliases: `record`. Force the bot to join and record your current channel                                                                       |
| `!leave`                                                      | Aliases: `stop`. Force the bot to leave it's current channel                                                                                   |
| `!miab [seconds] [voice channel]`                             | Echos back the input number of seconds of the recording into the voice channel specified and then rejoins original channel (max 120 seconds)   |
| `!prefix [character]`                                         | Aliases: `symbol`. Sets the prefix for each command to avoid conflict with other bots _(Default is '!')_                                       |
| `!removeAlias [alias name]`                                   | Removes an alias from a command.                                                                                                               |
| `!save \| !save [text channel output]`                        | Saves the current recording and outputs it to the current or specified text chats (caps at 16MB)                                               |
| `!saveLocation \| !saveLocation [text channel name]`          | Sets the text channel of message or the text channel specified as the default location to send files                                           |
| `!volume [1-100]`                                             | Sets the percentage volume to record at, from 1-100%                                                                                           |

_Replace brackets [] with item specified. Vertical bar | means 'or', either side of bar is valid choice._

## Deployment

The main motivation for forking the project was to allow any Discord Guild owner to be able to host
their own instance of this bot. In order to be able to do that, a bit of configuration is necessary.

- Create a [Discord Application](https://discordapp.com/developers/application). You'll need to have both the
**Client ID** and App Bot User's **Token** available to configure the bot.
 
- Download the [latest release](https://github.com/guacamoledragon/throw-voice/releases) of
the bot, you're looking for `throw-voice-<version>.jar`.

- Install Java 8

- Set the following environment variables:
  - `PORT`: Port on which the bot will run it's HTTP server on, strictly speaking not necessary,
  but it's convenient.
  - `CLIENT_ID`: Your Discord App Client ID
  - `BOT_TOKEN`: Your Discord App's App Bot User Token (what a mouthful!)

- Start the bot by running:
  - `java -jar throw-voice-<version>.jar`

- Navigate to `http://localhost:<PORT>` to add the bot to your Guild. 

Done!

_Note: If you're deploying on your own VPS, then you'll need a bit more setup as you may be using NGINX or Apache._

### Deployment with Heroku Button

You can deploy on Heroku by clicking on the button below and entering the `CLIENT_ID` and `BOT_TOKEN`.

[![Deploy](https://www.herokucdn.com/deploy/button.svg)](https://heroku.com/deploy)

If using the Hobby tier on Heroku, one big caveat is that the Heroku Dyno will sleep after 30 minutes
of inactivity. This will also make your bot go to sleep and will not respond to any of your commands.
To make waking up a bit easier, I've added an endpoint `/ping` that should respond with `pong` when the
Dyno wakes up.

Simply, visit `https://<heroku-app-name>.herokuapp.com/ping` to wake the bot before you start issuing
commands.

## Attributions

- Japanese Dragon icon made by [Freepik](http://www.freepik.com) from [www.flaticon.com](http://www.flaticon.com) is licensed by [CC 3.0 BY](http://creativecommons.org/licenses/by/3.0/)
- Original codebase by [ajm1996](https://github.com/ajm1996)
