package tech.gdragon.commands.settings;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import tech.gdragon.DiscordEcho;
import tech.gdragon.commands.Command;


public class VolumeCommand implements Command {

  @Override
  public Boolean called(String[] args, GuildMessageReceivedEvent e) {
    return true;
  }

  @Override
  public void action(String[] args, GuildMessageReceivedEvent e) {
    if (args.length != 1) {
      String prefix = DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix;
      DiscordEcho.sendMessage(e.getChannel(), usage(prefix));
      return;
    }

    try {
      int num = Integer.parseInt(args[0]);

      if (num > 0 && num <= 100) {
        double percent = (double) num / 100.0;
        DiscordEcho.serverSettings.get(e.getGuild().getId()).volume = percent;
        DiscordEcho.writeSettingsJson();

        DiscordEcho.sendMessage(e.getChannel(), "Volume set to " + num + "% for next recording!");

      } else {
        String prefix = DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix;
        DiscordEcho.sendMessage(e.getChannel(), usage(prefix));
        return;
      }

    } catch (Exception ex) {
      String prefix = DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix;
      DiscordEcho.sendMessage(e.getChannel(), usage(prefix));
      return;
    }
  }

  @Override
  public String usage(String prefix) {
    return prefix + "volume [1-100]";
  }

  @Override
  public String descripition() {
    return "Sets the percentage volume to record at, from 1-100%";
  }

  @Override
  public void executed(boolean success, GuildMessageReceivedEvent e) {
    return;
  }
}
