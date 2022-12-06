### record
> Record voice channel

This command is the reason you're using this bot, so if something isn't working as expected, please report in the support server.

## How to Use

Join a voice channel, then use this command. [pawa](https://pawa.im) will then:

* Join the voice channel and start recording, **unless**
* [pawa](https://pawa.im) is in another voice channel then it'll say it's already recording

!> You **must** use [`save`](commands/prefix/save.md) to save the recording otherwise, [pawa](https://pawa.im) will delete it

[pawa](https://pawa.im) will stop recording, when:

* everyone leaves the voice channel _(unless this setting was changed, see [`autostop`](commands/prefix/autostop.md))_
* or no audio detected for **2 minutes**

?> Enable [`autosave`](commands/prefix/autosave.md) to save all recordings

## Example
```
!record
```
<details>
  <summary>Example</summary>

  ```
  !record
  ```
</details>
