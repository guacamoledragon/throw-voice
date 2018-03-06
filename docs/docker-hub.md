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

# Supported tags and respective `Dockerfile` links

- [`1.1.0, latest` (*Dockerfile*)](https://github.com/guacamoledragon/throw-voice/blob/v1.1.0/Dockerfile)
- [`1.0.0` (*Dockerfile*)](https://github.com/guacamoledragon/throw-voice/blob/v1.0.0/Dockerfile)

# How to use this image

The following environment variables are necessary:

## Bot variables, **_required_**

- `BOT_TOKEN`: Discord Bot Token
- `CLIENT_ID`: Discord Client ID
- `DATA_DIR`: Directory path to store bot files
- `PORT`: Port on which the bot will run it's HTTP server on and redirect to bot's invite URL

## BackBlaze B2 Cloud Storage variables, **_required_**

For more information on these see [https://www.backblaze.com/b2/docs/](https://www.backblaze.com/b2/docs/).

- `B2_APP_KEY`: Application Key
- `B2_ACCOUNT_ID`: Account ID
- `B2_BASE_URL` _(optional)_: The base URL to use for B2, only useful if you're choosing a custom URL, don't set otherwise.
- `B2_BUCKET_ID`: Bucket ID
- `B2_BUCKET_NAME`: Bucket Name

## Rollbar variables, _optional_

All of these are optional, but if you want to upload your logs to Rollbar, create a
[Rollbar account](https://rollbar.com/signup/).

- `ROLLBAR_ENV`: Logging environment
- `ROLLBAR_TOKEN`: Application Token

The easiest thing to do is to create a `.env` file with all the environment variables:

    docker run -it --env-file .env --env JAVA_OPTS="-Xmx512m --add-modules java.xml.bind" -p 8080:8080 gdragon/throw-voice

The bot will automatically connect to your Guild if it has connected before, otherwise visit `http://localhost:8080` to
add to your Guild.

