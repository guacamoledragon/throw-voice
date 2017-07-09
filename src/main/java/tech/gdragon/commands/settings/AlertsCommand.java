package tech.gdragon.commands.settings;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import tech.gdragon.DiscordBot;
import tech.gdragon.commands.Command;


public class AlertsCommand implements Command {

  @Override
  public Boolean called(String[] args, GuildMessageReceivedEvent e) {
    return true;
  }

  @Override
  public void action(String[] args, GuildMessageReceivedEvent e) {
    if (args.length != 1) {
      String prefix = DiscordBot.serverSettings.get(e.getGuild().getId()).prefix;
      DiscordBot.sendMessage(e.getChannel(), usage(prefix));
      return;
    }

    String prefix = DiscordBot.serverSettings.get(e.getGuild().getId()).prefix;

    if (args[0].equals("off")) {
      DiscordBot
        .serverSettings.get(e.getGuild().getId())
        .alertBlackList.add(e.getAuthor().getId());

      e.getChannel()
        .sendMessage("Alerts now off, message `" + prefix + "alerts on` to re-enable at any time")
        .queue();
      DiscordBot.writeSettingsJson();

    } else if (args[0].equals("on")) {
      DiscordBot
        .serverSettings.get(e.getGuild().getId())
        .alertBlackList.remove(e.getAuthor().getId());

      e.getChannel()
        .sendMessage("Alerts now on, message `" + prefix + "alerts off` to disable at any time")
        .queue();

      DiscordBot.writeSettingsJson();
    } else {
      DiscordBot.sendMessage(e.getChannel(), usage(prefix));
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
