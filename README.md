# pawa
> Simple voice recording for Discord

<div align="center">
  <a href="https://discordbots.org/bot/338897906524225538">
    <img src="https://discordbots.org/api/widget/338897906524225538.png" alt="Discord Bots" />
  </a>

  [![Discord](https://discordapp.com/api/guilds/408795211901173762/widget.png)](https://discord.gg/gkvsNw8)
  [![pipeline status](https://gitlab.com/pawabot/pawa/badges/master/pipeline.svg)](https://gitlab.com/pawabot/pawa/commits/master)
  [![Get your own version badge on microbadger.com](https://images.microbadger.com/badges/version/gdragon/throw-voice.svg)](https://microbadger.com/images/gdragon/throw-voice)
  [![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgitlab.com%2Fpawabot%2Fpawa.svg?type=shield)](https://app.fossa.io/projects/git%2Bgitlab.com%2Fpawabot%2Fpawa?ref=badge_shield)

</div>

# Usage

`pawa` is a Discord bot that allows you to record a voice channel with ease. Follow this recipe:

1. Join a voice channel, then type `!record`
1. Create and upload recording, type `!save`
1. Stop recording voice channel, type `!stop`

<video loop muted controls>
  <source src="./assets/features/pawa-howto.webm" type="video/webm">
  <source src="./assets/features/pawa-howto.mp4" type="video/mp4">
</video>

# Support

You can support `pawa` development in any of these ways:

**Free**

* 🐤 Follow the [Twitter account](https://twitter.com/pawa_bot) and retweet
* 🗳️ Vote on [top.gg](https://top.gg/bot/pawa/vote)
* ⭐ Leave a review on [bots.ondiscord.xyz](https://bots.ondiscord.xyz/bots/338897906524225538)
* 🔄 Share with others!

**Affiliates**

If you want to try hosting your own bot, or website. I've used these hosting platforms in the past, use my referral links:

* **Limited Time** [Vultr](https://www.vultr.com/?ref=8483036-6G): $100 credit to spend over 30 days
* [Linode](https://www.linode.com/?r=e655d87b0d382f2922e75de841b2f19d7403e2ca)
* [ServerCheap](https://servercheap.net/crm/aff.php?aff=324)
    * Current Provider
* We collect anonymous basic analytics using [Simple Analytics](https://referral.simpleanalytics.com/pawa), see them live here: [https://simpleanalytics.com/pawa.im](https://simpleanalytics.com/pawa.im)

**Non-Free**

_There is no obligation to do any of this, but this is a way to express your gratitude towards `pawa`_

* [Buy me a coffee](https://ko-fi.com/L3L215SZC)
* [Github Sponsor](https://github.com/sponsors/jvtrigueros)
* [Donate Bot](https://donatebot.io/checkout/408795211901173762)

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
# Documentation

- [Full Command List](#full-command-list)
    - [alias](#alias)
    - [autorecord](#autorecord)
    - [autostop](#autostop)
    - [autosave](#autosave)
    - [clip](#clip)
    - [record](#record)
    - [stop](#stop)
    - [prefix](#prefix)
    - [removeAlias](#removealias)
    - [save](#save)
    - [saveLocation](#savelocation)
    - [volume](#volume)
- [Self-hosting](#self-hosting)
- [Attributions](#attributions)
- [License](#license)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## Full Command List

These commands aren't necessary for the functionality of `pawa` but they do provide some quality of life improvements.

**Note: _Anything between <> is mandatory. Anything in [] is optional. Vertical bar | means 'or', either side of bar is valid choice._**

### alias
> Creates an alias, or alternate name, to a command for customization.

```
!alias <command> <custom alias>
```
<details>
  <summary>Example</summary>

  ```
  !alias record r
  ```
</details>

### autorecord
> Sets the number of players for the bot to autorecord a voice channel, or disables autorecord-ing. 'all' will apply number to all voice channels.

```
!autorecord <voice-channel | all> <number | off>`
```
<details>
  <summary>Example</summary>

  ```
  !autorecord bot-testing 10
  !autorecord bot-testing off
  !autorecord all 3
  !autorecord all off
  ```
</details>

### autostop
> Sets the number of players for the bot to autostop a voice channel, or disables autostop-ing. 'all' will apply number to all voice channels.    

```
!autostop <voice-channel | all> <number>
```
<details>
  <summary>Example</summary>

  ```
  !autostop bot-testing 10
  !autostop bot-testing off
  !autostop all 3
  !autostop all off
  ```
</details>

### autosave
> Toggles the option to automatically save and send all files at the end of each session - not just saved or clipped files

```
!autosave
```
<details>
  <summary>Example</summary>

  ```
  !autosave
  ```
</details>

### clip
> Saves a clip of the specified length and outputs it in the current or specified text channel (max 120 seconds)

```
!clip <seconds> [text-channel]
```
<details>
  <summary>Example</summary>

  ```
  !clip 10
  !clip 10 bot-testing
  ```
</details>

### record
> Record voice channel

```
!record
```
<details>
  <summary>Example</summary>

  ```
  !record
  ```
</details>

### stop
> Stop recording voice channel

```
!stop
```
<details>
  <summary>Example</summary>

  ```
  !stop
  ```
</details>

### prefix
> Change prefix _(Default is '!')_

```
!prefix <new-prefix>
```
<details>
  <summary>Example</summary>

  ```
  !prefix $
  ```
</details>

### removeAlias
> Removes an alias from a command.

```
!removeAlias <alias-name>
```
<details>
  <summary>Example</summary>

  ```
  !removeAlias r
  ```
</details>

### save
> Saves the current recording and uploads it to current, `saveLocation`, or specified text channel. _(Max recording is 110MB)_

```
!save [text-channel]
```
<details>
  <summary>Example</summary>

  ```
  !save
  !save bot-testing
  ```
</details>

### saveLocation
> Default text channel for all messages. Use `off` to restore default behaviour.

```
!saveLocation [text-channel | off]
```
<details>
  <summary>Example</summary>

  ```
  !saveLocation
  !saveLocation bot-testing
  !saveLocation off
  ```
</details>

### volume
> Set recording volume, range 1-100%. _(default 100%)_

```
!volume <1-100>
```
<details>
<summary>Example</summary>

```
!volume 80
```
</details>

## Self-hosting

Self-hosting instructions got a lot more complex, see the [self-hosting](./docs/self-hosting.md) section for more on how
you can deploy the bot.

## Attributions

- "Dragon" by [lastspark](https://thenounproject.com/lastspark) from [the Noun Project](http://thenounproject.com/).
- Original Java codebase by [ajm1996's](https://github.com/ajm1996) [DiscordEcho](https://github.com/ajm1996/DiscordEcho).

## License

```
Copyright (c) 2017-2020 Guacamole Dragon, LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
