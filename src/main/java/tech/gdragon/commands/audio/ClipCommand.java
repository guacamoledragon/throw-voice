package tech.gdragon.commands.audio;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import tech.gdragon.BotUtils;
import tech.gdragon.DiscordBot;
import tech.gdragon.commands.Command;
import tech.gdragon.db.Shim;
import tech.gdragon.db.dao.Guild;


public class ClipCommand implements Command {
  @Override
  public void action(String[] args, GuildMessageReceivedEvent e) {
    String prefix =
      Shim.INSTANCE.xaction(() -> {
        Guild guild = Guild.Companion.findById(e.getGuild().getIdLong());
        return guild != null ? guild.getSettings().getPrefix() : "!";
      });

    if (args.length != 1 && args.length != 2) {
      BotUtils.sendMessage(e.getChannel(), usage(prefix));
      return;
    }

    if (e.getGuild().getAudioManager().getConnectedChannel() == null) {
      BotUtils.sendMessage(e.getChannel(), "I wasn't recording!");
      return;
    }

    //cut off # in channel name if they included it
    if (args.length == 2 && args[1].startsWith("#")) {
      args[1] = args[1].substring(1);
    }

    if (args.length == 2 && e.getGuild().getTextChannelsByName(args[1], true).size() == 0) {
      BotUtils.sendMessage(e.getChannel(), "Cannot find specified text channel");
      return;
    }

    int time;
    try {
      time = Integer.parseInt(args[0]);
    } catch (Exception ex) {
      BotUtils.sendMessage(e.getChannel(), usage(prefix));
      return;
    }

    if (time <= 0) {
      BotUtils.sendMessage(e.getChannel(), "Time must be greater than 0");
      return;
    }

    /*if (args.length == 2) {
      DiscordBot.writeToFile(e.getGuild(), time, e.getGuild().getTextChannelsByName(args[1], true).get(0));
    } else {
      DiscordBot.writeToFile(e.getGuild(), time, e.getChannel());
    }*/

  }

  @Override
  public String usage(String prefix) {
    return prefix + "clip [seconds] | clip [seconds] [text channel output]";
  }

  @Override
  public String description() {
    return "Saves a clip of the specified length and outputs it in the current or specified text channel (max 120 seconds)";
  }
}
