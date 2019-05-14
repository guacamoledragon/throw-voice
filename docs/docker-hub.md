# throw-voice
> A voice channel recording bot for Discord.

<p align="center">
  <a href="https://discordbots.org/bot/338897906524225538">
    <img src="https://discordbots.org/api/widget/338897906524225538.png" alt="Discord Bots" />
  </a>
</p>

[![Discord](https://discordapp.com/api/guilds/408795211901173762/widget.png)](https://discord.gg/gkvsNw8)
[![CircleCI](https://circleci.com/gh/guacamoledragon/throw-voice.svg?style=svg)](https://circleci.com/gh/guacamoledragon/throw-voice)
[![codecov](https://codecov.io/gh/guacamoledragon/throw-voice/branch/master/graph/badge.svg)](https://codecov.io/gh/guacamoledragon/throw-voice)
[![Get your own version badge on microbadger.com](https://images.microbadger.com/badges/version/gdragon/throw-voice.svg)](https://microbadger.com/images/gdragon/throw-voice)
[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Fguacamoledragon%2Fthrow-voice.svg?type=shield)](https://app.fossa.io/projects/git%2Bgithub.com%2Fguacamoledragon%2Fthrow-voice?ref=badge_shield)

# Supported tags and respective `Dockerfile` links

- [`1.1.2, latest` (*Dockerfile*)](https://github.com/guacamoledragon/throw-voice/blob/v1.1.2/Dockerfile)
- [`1.1.1` (*Dockerfile*)](https://github.com/guacamoledragon/throw-voice/blob/v1.1.1/Dockerfile)
- [`1.1.0` (*Dockerfile*)](https://github.com/guacamoledragon/throw-voice/blob/v1.1.0/Dockerfile)
- [`1.0.0` (*Dockerfile*)](https://github.com/guacamoledragon/throw-voice/blob/v1.0.0/Dockerfile)

# How to use this image

The following environment variables are necessary:

## Bot variables, **_required_**

- `BOT_TOKEN`: Discord Bot Token
- `DATA_DIR`: Directory path to store bot files
- `DISCORD_WEBHOOK` _(optional)_: The location of the Discord webhook where error logs will be sent
- `PORT`: Port on which the bot will run it's HTTP server on and redirect to bot's invite URL

## Minio Storage variables, **_required_**

For more information on this see [Minio](https://www.minio.io/).

> In the past, I used BackBlaze B2, but with Minio the user can provide their own data store.

- `DS_ACCESS_KEY` _(optional)_: Access Key
- `DS_SECRET_KEY` _(optional)_: Application Key
- `DS_BASEURL` _(optional)_: Only useful if you're choosing a custom URL, don't set otherwise.
- `DS_BUCKET`: Bucket Name
- `DS_HOST`: Minio host url e.g _http://localhost:9000_

## Rollbar variables, _optional_

All of these are optional, but if you want to upload your logs to Rollbar, create a
[Rollbar account](https://rollbar.com/signup/).

- `ROLLBAR_ENV`: Logging environment
- `ROLLBAR_TOKEN`: Application Token

The easiest thing to do is to create a `.env` file with all the environment variables:

    docker run -it --env-file .env --env JAVA_OPTS="-Xmx512m --add-modules java.xml.bind" -p 8080:8080 gdragon/throw-voice

The bot will automatically connect to your Guild if it has connected before, otherwise visit `http://localhost:8080` to
add to your Guild.

