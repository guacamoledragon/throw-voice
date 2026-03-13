## autostop
> Automatically stop recording channel if there are less than `threshold` number of people in the voice channel. If `threshold` is **off** or **0** then autostop is disabled for that channel.

!> **Deprecated:** Prefix commands will be removed on May 1, 2026. Please use [slash commands](/commands.md#slash-commands) instead.

```
!autostop <voice-channel | all> <threshold>
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
