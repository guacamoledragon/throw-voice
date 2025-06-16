package tech.gdragon.listener

import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.commands.updateCommands
import net.dv8tion.jda.api.sharding.ShardManager
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.audio.BetaRecord
import tech.gdragon.commands.audio.BetaSave

fun setupBetaCommands(manager: ShardManager, pawa: Pawa) {
  manager.run {
    onCommand("beta-record", consumer = BetaRecord.handler(pawa))
    onCommand("beta-save", consumer = BetaSave.handler(pawa))

    guilds.forEach { guild ->
      guild.updateCommands {
        addCommands(
          BetaRecord.command,
          BetaSave.command,
        )
      }.queue()
    }
  }
}
