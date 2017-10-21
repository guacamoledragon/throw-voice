package tech.gdragon.commands.settings;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import tech.gdragon.DiscordBot;
import tech.gdragon.commands.Command;

@Deprecated
public class PrefixCommand implements Command {
  @Override
  public void action(String[] args, GuildMessageReceivedEvent e) {
    if (args[0].length() != 1 || args.length != 1) {
      String prefix = DiscordBot.serverSettings.get(e.getGuild().getId()).prefix;
      DiscordBot.sendMessage(e.getChannel(), usage(prefix));
      return;
    }

    DiscordBot.serverSettings.get(e.getGuild().getId()).prefix = args[0];
    DiscordBot.writeSettingsJson();

    DiscordBot.sendMessage(e.getChannel(), "Command prefix now set to " + args[0]);
  }

  @Override
  public String usage(String prefix) {
    return prefix + "prefix [character]";
  }

  @Override
  public String description() {
    return "Sets the prefix for each command to avoid conflict with other bots (Default is '!')";
  }
}
