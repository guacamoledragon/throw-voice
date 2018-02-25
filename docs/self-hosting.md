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
  - [Heroku Button](#heroku-button)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Pre-Requisites

- Create a [Discord Application](https://discordapp.com/developers/application).
  - You'll need to have both the **Client ID** and **App Bot Token** available to configure the bot.
- Create a [BackBlaze B2 Cloud Storage](https://www.backblaze.com/b2/cloud-storage.html) account
  - You'll need to create a bucket, take a note of the following:
    - Bucket ID
    - Bucket Name
    - Account ID
    - Application Key

## Local (or VPS)

These instructions are for those who want to run the bot either on their computer or on a server, for this you'll need to
have [install Java 9 JRE](http://www.oracle.com/technetwork/java/javase/downloads/index.html).

- Download the [latest release](https://github.com/guacamoledragon/throw-voice/releases) of the bot and extract it,
  you're looking for `throw-voice-<version>-release.zip`.

- Configure Bot
  - Windows Users:
    - Edit `start.bat` and enter the appropriate values for all the variables described under [environment variables](./environment-variables.md).
    - Save `start.bat`, start bot by double-click'ing `start.bat`
  - Linux Users:
    - Drop down to a shell and set all the variables described under [environment variables](./environment-variables.md).
    - Start bot by running: `java -Xmx512m --add-modules java.xml.bind -cp 'throw-voice-<version>.jar:lib\*' tech.gdragon.App`

- Navigate to `http://localhost:<PORT>` to add the bot to your Guild. 

Done!

_Note: If you're deploying on your own VPS, then you'll need a bit more setup as you may be using NGINX or Apache._

## Docker

If Docker is your jam, the easiest thing to do is to create a `.env` file with all the variables described under
[environment variables](./environment-variables.md). Then:

    docker run -it --env-file .env -p <PORT>:<PORT> gdragon/throw-voice:<version>

This repo also contains a [`docker-compose.yml`](../docker-compose.yml) that can be used if desired, but it's mostly used
for my deployment purposes.

See the [Docker Hub](https://hub.docker.com/r/gdragon/throw-voice/) registry for more details on the container.

## Heroku Button

_Currently not working, see [#17](https://github.com/guacamoledragon/throw-voice/issues/17)_

You can deploy on Heroku by clicking on the button below and entering the `CLIENT_ID` and `BOT_TOKEN`.

[![Deploy](https://www.herokucdn.com/deploy/button.svg)](https://heroku.com/deploy)

If using the Hobby tier on Heroku, one big caveat is that the Heroku Dyno will sleep after 30 minutes
of inactivity. This will also make your bot go to sleep and will not respond to any of your commands.
To make waking up a bit easier, I've added an endpoint `/ping` that should respond with `pong` when the
Dyno wakes up.

Simply, visit `https://<heroku-app-name>.herokuapp.com/ping` to wake the bot before you start issuing
commands.
