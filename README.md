# throw-voice
> A voice channel recording bot for Discord.

[![Build Status](https://travis-ci.org/guacamoledragon/throw-voice.svg?branch=master)](https://travis-ci.org/guacamoledragon/throw-voice)  

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

## Deployment

The main motivation for forking the project was to allow any Discord Guild owner to be able to host
their own instance of this bot. In order to be able to do that, a bit of configuration is necessary.

- First, you'll need to create an App Bot User, do so by creating a
[Discord Application](https://discordapp.com/developers/application). You'll need to have both the
**Client ID** and App Bot User's **Token** available to configure the bot.
 
- Next, download the [latest release](https://github.com/guacamoledragon/throw-voice/releases) of
the bot, you're looking for `throw-voice-<version>.jar`.

- Install Java 8, the JRE is plenty.

- Before running the bot, you'll need to set a few environment variables:
  - `PORT`: Port on which the bot will run it's HTTP server on, strictly speaking not necessary,
  but it's convenient.
  - `CLIENT_ID`: Your Discord App Client ID
  - `BOT_TOKEN`: Your Discord App's App Bot User Token (what a mouthfull!)

- Finally, after all these are set, start the bot by running:
  - `java -jar throw-voice-<version>.jar`

Done!

You can then navigate to `http://localhost:<PORT>`, which will redirect you to your bot's permission
page, where you can link it to your Guild.

### Deployment with Heroku Button

If you wish to deploy an instance of this bot on Heroku, all you need to is click on the Heroku Button below:

[![Deploy](https://www.herokucdn.com/deploy/button.svg)](https://heroku.com/deploy)
 

## Attributions

- Japanese Dragon icon made by [Freepik](http://www.freepik.com) from [www.flaticon.com](http://www.flaticon.com) is licensed by [CC 3.0 BY](http://creativecommons.org/licenses/by/3.0/)
- Original codebase by [ajm1996](https://github.com/ajm1996)
