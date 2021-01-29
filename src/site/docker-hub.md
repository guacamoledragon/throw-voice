<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**  *generated with [DocToc](https://github.com/thlorenz/doctoc)*

- [pawa](#pawa)
- [Supported tags and respective `Dockerfile` links](#supported-tags-and-respective-dockerfile-links)
- [How to use this image](#how-to-use-this-image)
  - [Bot variables, **_required_**](#bot-variables-_required_)
  - [Bot variables, additional configs _optional_](#bot-variables-additional-configs-_optional_)
  - [Minio Storage variables, **_required_**](#minio-storage-variables-_required_)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# pawa
> Simple voice recording for Discord

<div align="center">
  <a href="https://discordbots.org/bot/338897906524225538">
    <img src="https://discordbots.org/api/widget/338897906524225538.png" alt="Discord Bots" />
  </a>
</div>

[![Discord](https://discordapp.com/api/guilds/408795211901173762/widget.png)](https://discord.gg/gkvsNw8)
[![pipeline status](https://gitlab.com/pawabot/pawa/badges/master/pipeline.svg)](https://gitlab.com/pawabot/pawa/commits/master)
[![codecov](https://codecov.io/gl/pawabot/pawa/branch/master/graph/badge.svg)](https://codecov.io/gl/pawabot/pawa)
[![Get your own version badge on microbadger.com](https://images.microbadger.com/badges/version/gdragon/throw-voice.svg)](https://microbadger.com/images/gdragon/throw-voice)
[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgitlab.com%2Fpawabot%2Fpawa.svg?type=shield)](https://app.fossa.io/projects/git%2Bgitlab.com%2Fpawabot%2Fpawa?ref=badge_shield)

# Supported tags and respective `Dockerfile` links

- [`1.2.0, latest` (*Dockerfile*)](https://gitlab.com/pawabot/pawa/blob/v1.2.0/Dockerfile)
- [`1.1.2` (*Dockerfile*)](https://gitlab.com/pawabot/pawa/blob/v1.1.2/Dockerfile)
- [`1.1.1` (*Dockerfile*)](https://gitlab.com/pawabot/pawa/blob/v1.1.1/Dockerfile)
- [`1.1.0` (*Dockerfile*)](https://gitlab.com/pawabot/pawa/blob/v1.1.0/Dockerfile)
- [`1.0.0` (*Dockerfile*)](https://gitlab.com/pawabot/pawa/blob/v1.0.0/Dockerfile)

# How to use this image

The following environment variables are necessary:

## Bot variables, **_required_**

- `BOT_TOKEN`: Discord Bot Token
- `BOT_DATA_DIR`: Directory path to store bot files
- `BOT_HTTP_PORT`: Port on which the bot will run it's HTTP server on and redirect to bot's invite URL

## Bot variables, additional configs _optional_

- `DISCORD_WEBHOOK`: The location of the Discord webhook where error logs will be sent
- `BOT_LEAVE_GUILD_AFTER`: Number, in days, before bot leaves a server for inactivity
  - Defaults to `30`
  - Set to `0` to disable
- `BOT_FILE_FORMAT`: Save recordings as RAW PCM files instead of MP3
  - Defaults to `false`
- `VERSION`: The version to display as the bot's _Playing ..._ status
- `BOT_WEBSITE`: The website to display as the bot's _Playing ..._ status

## Minio Storage variables, **_required_**

For more information on this see [Minio](https://www.minio.io/).

- `DS_ACCESS_KEY`: Access Key
- `DS_SECRET_KEY`: Application Key
- `DS_BASEURL` _(optional)_: Only useful if you're choosing a custom URL, don't set otherwise.
- `DS_BUCKET`: Bucket Name
- `DS_HOST`: Minio host url e.g _http://localhost:9000_
