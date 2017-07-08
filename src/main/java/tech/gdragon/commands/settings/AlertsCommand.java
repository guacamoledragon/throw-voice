package tech.gdragon.commands.settings;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import tech.gdragon.DiscordEcho;
import tech.gdragon.commands.Command;


public class AlertsCommand implements Command {

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

    String prefix = DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix;

    if (args[0].equals("off")) {
      DiscordEcho
        .serverSettings.get(e.getGuild().getId())
        .alertBlackList.add(e.getAuthor().getId());

      e.getChannel()
        .sendMessage("Alerts now off, message `" + prefix + "alerts on` to re-enable at any time")
        .queue();
      DiscordEcho.writeSettingsJson();

    } else if (args[0].equals("on")) {
      DiscordEcho
        .serverSettings.get(e.getGuild().getId())
        .alertBlackList.remove(e.getAuthor().getId());

      e.getChannel()
        .sendMessage("Alerts now on, message `" + prefix + "alerts off` to disable at any time")
        .queue();

      DiscordEcho.writeSettingsJson();
    } else {
      DiscordEcho.sendMessage(e.getChannel(), usage(prefix));
    }
  }

  @Override
  public String usage(String prefix) {
    return prefix + "alerts [on | off]";
  }

  @Override
  public String descripition() {
    return "Turns on/off direct message alerts for when you are being recorded in a voice channel "
      + "(on by default)";
  }

  @Override
  public void executed(boolean success, GuildMessageReceivedEvent e) {
  }
}
