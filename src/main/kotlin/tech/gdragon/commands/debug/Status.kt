package tech.gdragon.commands.debug

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import tech.gdragon.BotUtils
import tech.gdragon.commands.CommandHandler
import tech.gdragon.commands.InvalidCommand
import tech.gdragon.i18n.Lang

class Status : CommandHandler() {
  private fun addShardStatusField(builder: EmbedBuilder, shard: JDA): EmbedBuilder {
    val status = shard.status
    val emoji = if (JDA.Status.CONNECTED < status) ":x:" else ":white_check_mark:"

    return builder
      .addField("$emoji Shard ${shard.shardInfo.shardId}", status.toString(), true)
  }

  override fun action(args: Array<String>, event: GuildMessageReceivedEvent) {
    require(args.isEmpty()) {
      throw InvalidCommand(::usage, "Incorrect number of arguments: ${args.size}")
    }

    // TODO: Hardcoding my Discord User ID because I'm lazy
    require(standalone || 96802905322962944L == event.author.idLong) {
      throw InvalidCommand({ "Command can only be used by server admins." }, "Unauthorized use.")
    }

    val shardManager = event.jda.shardManager

    val embedBuilder = EmbedBuilder()
      .setTitle(":fleur_de_lis: Server Status :fleur_de_lis:")
      .setDescription("Summary of shard status.")

    shardManager
      ?.shards
      ?.reversed()
      ?.fold(embedBuilder, ::addShardStatusField)
      ?.build()
      ?.let { embed ->
        BotUtils.sendEmbedMessage(event.channel, embed)
      }
      ?: BotUtils.sendMessage(event.channel, "Couldn't report status!")
  }

  override fun usage(prefix: String, lang: Lang): String = "${prefix}status"

  override fun description(lang: Lang): String = "Display Shard Status"

}
