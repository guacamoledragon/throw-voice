# Self-hosting
The main motivation for forking the project was to allow any Discord Guild owner to be able to host their own instance
of this bot. In order to be able to do that, a bit of configuration is necessary, however, here's nothing special about
this bot, it can be self-hosted without any issues in one of the following ways.

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
## Contents

- [Discord Pre-Requisites](#discord-pre-requisites)
  - [Local (or VPS)](#local-or-vps)
  - [Docker](#docker)
  - [Heroku Button](#heroku-button)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## Discord Pre-Requisites

- Create a [Discord Application](https://discordapp.com/developers/application). You'll need to have both the
**Client ID** and App Bot User's **Token** available to configure the bot.

### Local (or VPS)

- Download the [latest release](https://github.com/guacamoledragon/throw-voice/releases) of
the bot, you're looking for `throw-voice-<version>.jar`.

- Install Java 9

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

### Docker

If Docker is your jam, then you can start the bot using:

    docker run -it -e PORT=8080 -e CLIENT_ID=... -e BOT_TOKEN=... -p 8080:8080 gdragon/throw-voice

Replace `CLIENT_ID` and `BOT_TOKEN` with your correct values. The port isn't fixed, it can be anything you'd like.

See the [Docker Hub](https://hub.docker.com/r/gdragon/throw-voice/) registry for more details on the container.

### Heroku Button

_Currently not working, see [#17](https://github.com/guacamoledragon/throw-voice/issues/17)_

You can deploy on Heroku by clicking on the button below and entering the `CLIENT_ID` and `BOT_TOKEN`.

[![Deploy](https://www.herokucdn.com/deploy/button.svg)](https://heroku.com/deploy)

If using the Hobby tier on Heroku, one big caveat is that the Heroku Dyno will sleep after 30 minutes
of inactivity. This will also make your bot go to sleep and will not respond to any of your commands.
To make waking up a bit easier, I've added an endpoint `/ping` that should respond with `pong` when the
Dyno wakes up.

Simply, visit `https://<heroku-app-name>.herokuapp.com/ping` to wake the bot before you start issuing
commands.
