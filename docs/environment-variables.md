# Environment Variables

These are the environment configuration variables used by the bot.

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
## Contents

- [Bot variables, **_required_**](#bot-variables-_required_)
- [Minio Storage variables, **_required_**](#minio-storage-variables-_required_)
- [Rollbar variables, _optional_](#rollbar-variables-_optional_)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## Bot variables, **_required_**

- `BOT_TOKEN`: Discord Bot Token
- `DATA_DIR`: Directory path to store bot files
- `DISCORD_WEBHOOK` _(optional)_: The location of the Discord webhook where error logs will be sent
- `PORT`: Port on which the bot will run it's HTTP server on and redirect to bot's invite URL

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
