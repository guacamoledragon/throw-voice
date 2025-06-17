package tech.gdragon.listener

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.slash
import dev.minn.jda.ktx.interactions.commands.subcommand
import dev.minn.jda.ktx.interactions.commands.updateCommands
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.sharding.ShardManager
import tech.gdragon.api.pawa.Pawa
import tech.gdragon.commands.audio.BetaRecord
import tech.gdragon.commands.audio.BetaSave
import tech.gdragon.commands.settings.BetaIgnore

fun setupBetaCommands(manager: ShardManager, pawa: Pawa) {
  manager.run {
    onCommand("beta") { event ->
      when (event.subcommandName) {
        BetaRecord.command.name -> BetaRecord.handler(pawa)(event)
        BetaSave.command.name -> BetaSave.handler(pawa)(event)
        BetaIgnore.command.name -> BetaIgnore.handler(pawa)(event)
        else -> event.reply_("Choose a beta command.").await()
      }
    }

    guilds.forEach { guild ->
      guild.updateCommands {
        slash("beta", "Commands in their beta phase.") {
          subcommand(BetaRecord.command.name, BetaRecord.command.description)
          subcommand(BetaSave.command.name, BetaSave.command.description)
          subcommand(BetaIgnore.command.name, BetaIgnore.command.description) {
            option<User>("user", "The user to ignore", true)
          }
        }
      }.queue()
    }
  }
}
