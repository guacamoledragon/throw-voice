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

## What is `pawa`?

`pawa` is a Discord bot that records any audio in a voice channel.

# Support
_There is no obligation to do any of this, but this is a way to express your gratitude towards `pawa`_

* [Buy me a coffee](https://ko-fi.com/L3L215SZC) _(One time)_
* [Github Sponsor](https://github.com/sponsors/jvtrigueros) _(Subscription)_

**Free**

* 🐤 Follow the [Twitter account](https://twitter.com/pawa_bot) and retweet
* 🗳️ Vote on [top.gg](https://top.gg/bot/pawa/vote)
* ⭐ Leave a review on [bots.ondiscord.xyz](https://bots.ondiscord.xyz/bots/338897906524225538)
* 🔄 Share with others!

**Affiliates**

If you want to try hosting your own bot, or website. I've used these hosting platforms in the past, use my referral links:

* **Limited Time** [Vultr](https://www.vultr.com/?ref=8483036-6G): $100 credit to spend over 30 days
* [DigitalOcean](https://m.do.co/c/d2af1fbee897): $100 credit
* [Linode](https://www.linode.com/?r=e655d87b0d382f2922e75de841b2f19d7403e2ca)
* We collect anonymous basic analytics using [Simple Analytics](https://referral.simpleanalytics.com/pawa)

# Full Command List

These commands aren't necessary for the functionality of `pawa` but they do provide some quality of life improvements.

?> _NOTE_ Anything between <> is mandatory. Anything in [] is optional. Vertical bar | means 'or', either side of bar is valid choice.

|                                  Command | Description                                                                                       |
|-----------------------------------------:|---------------------------------------------------------------------------------------------------|
|               [alias](commands/alias.md) | Creates an alias, or alternate name, to a command for customization.                              |
|    [autorecord](commands/autorecord.md)* | Configure the number of users in a voice channel before [pawa](https://pawa.im) begins recording. |
|         [autostop](commands/autostop.md) | Configure the number of users in a voice channel before [pawa](https://pawa.im) stops recording.  |
|         [autosave](commands/autosave.md) | Automatically save recording.                                                                     |
|             [record](commands/record.md) | Start recording.                                                                                  |
|                 [stop](commands/stop.md) | Stop recording.                                                                                   |
|             [prefix](commands/prefix.md) | Change prefix.                                                                                    |
|   [removeAlias](commands/removealias.md) | Remove custom alias.                                                                              |
|                 [save](commands/save.md) | Saves current recording, either provides a link or uploads directly to Discord.                   |
| [saveLocation](commands/savelocation.md) | Default text channel for all messages.                                                            |
|             [volume](commands/volume.md) | Set the recording volume.                                                                         |

?> *Command only available for supporters

# Self-hosting

Self-hosting instructions got a lot more complex, see the [self-hosting](self-hosting.md) section for more on how
you can deploy the bot.

# Attributions <!-- {docsify-ignore} -->

- Original Java codebase by [ajm1996's](https://github.com/ajm1996) [DiscordEcho](https://github.com/ajm1996/DiscordEcho).

# License

```
Copyright (c) 2017-2021 Guacamole Dragon, LLC

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
