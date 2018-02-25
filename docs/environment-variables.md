# Environment Variables

These are the environment configuration variables used by the bot.

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
## Contents

- [Bot variables, **_required_**](#bot-variables-_required_)
- [BackBlaze B2 Cloud Storage variables, **_required_**](#backblaze-b2-cloud-storage-variables-_required_)
- [Rollbar variables, _optional_](#rollbar-variables-_optional_)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

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
