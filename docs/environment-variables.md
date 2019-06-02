# Environment Variables

These are the environment configuration variables used by the bot.

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
## Contents

- [Bot variables, **_required_**](#bot-variables-_required_)
- [Bot variables, additional configs _optional_](#bot-variables-additional-configs-_optional_)
- [Minio Storage variables, **_required_**](#minio-storage-variables-_required_)
- [Rollbar variables, _optional_](#rollbar-variables-_optional_)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## Bot variables, **_required_**

- `BOT_TOKEN`: Discord Bot Token
- `DATA_DIR`: Directory path to store bot files
- `PORT`: Port on which the bot will run it's HTTP server on and redirect to bot's invite URL

## Bot variables, additional configs _optional_

- `DISCORD_WEBHOOK`: The location of the Discord webhook where error logs will be sent
- `BOT_LEAVE_GUILD_AFTER`: Number, in days, before bot leaves a server for inactivity
  - Defaults to `30`
  - Set to `0` to disable
- `PCM_MODE`: Save recordings as RAW PCM files instead of MP3
  - Defaults to `false`
- `VERSION`: The version to display as the bot's _Playing ..._ status
- `WEBSITE`: The website to display as the bot's _Playing ..._ status

## Minio Storage variables, **_required_**

For more information on this see [Minio](https://www.minio.io/).

- `DS_ACCESS_KEY`: Access Key
- `DS_SECRET_KEY`: Application Key
- `DS_BASEURL` _(optional)_: Only useful if you're choosing a custom URL, don't set otherwise.
- `DS_BUCKET`: Bucket Name
- `DS_HOST`: Minio host url e.g _http://localhost:9000_

## Rollbar variables, _optional_

All of these are optional, but if you want to upload your logs to Rollbar, create a
[Rollbar account](https://rollbar.com/signup/).

- `ROLLBAR_ENV`: Logging environment
- `ROLLBAR_TOKEN`: Application Token
