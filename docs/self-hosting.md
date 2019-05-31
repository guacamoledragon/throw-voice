# Self-hosting
The main motivation for forking the project was to allow any Discord Guild owner to be able to host their own instance
of this bot. In order to be able to do that, a bit of configuration is necessary, however, here's nothing special about
this bot, it can be self-hosted without any issues in one of the following ways.

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
## Contents

- [Pre-Requisites](#pre-requisites)
  - [Local (or VPS)](#local-or-vps)
  - [Docker](#docker)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Pre-Requisites

- Create a [Discord Application](https://discordapp.com/developers/application).
  - You'll need to have both the **Client ID** and **App Bot Token** available to configure the bot.
- A running instance of [Minio Cloud Storage](https://www.minio.io/)
  - Using Minio allows you to use whatever cloud storage solution you want (or none), but you'll need:
    - Bucket Name
      - **Make sure the bucket you choose for `DS_BUCKET` exists**
    - Access Key _(may be optional if your store does not require it)_
    - Secret Key _(may be optional if your store does not require it)_

## Local (or VPS)

These instructions are for those who want to run the bot either on their computer or on a server, for this you'll need to
have [install Java 11+](https://adoptopenjdk.net/).

- Download the [latest release](https://github.com/guacamoledragon/throw-voice/releases) of the bot and extract it,
  you're looking for `throw-voice-<version>.zip`.

- Configure Bot
  - Windows Users:
    - Edit `start.bat` and enter the appropriate values for all the variables described under [environment variables](./environment-variables.md).
    - Save `start.bat`, start bot by double-click'ing `start.bat`
  - Linux/macOS Users:
    - Edit `start.sh` and enter the appropriate values for all the variables described under [environment variables](./environment-variables.md).
    - Save `start.sh`, start bot by double-click'ing `start.sh`
      - If you don't have a GUI, then drop to a terminal and execute `start.sh`

- Navigate to `http://localhost:<PORT>` to add the bot to your Guild. 

Done!

_Note: If you're deploying on your own VPS, then you'll need a bit more setup as you may be using NGINX or Apache._

## Docker

If Docker is your jam, the easiest thing to do is to modify the `.env` file included with only the variables already included,
for reference see [environment variables](./environment-variables.md). Then use [`docker-compose`](https://docs.docker.com/compose/):

    docker-compose up # optionally, --detach

This will start both the bot and minio, and that's really all there is.
