package tech.gdragon.commands.settings;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import tech.gdragon.DiscordBot;
import tech.gdragon.commands.Command;


public class RemoveAliasCommand implements Command {

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

    if (!DiscordBot.serverSettings.get(e.getGuild().getId()).aliases.containsKey(args[0].toLowerCase())) {
      DiscordBot.sendMessage(e.getChannel(), "Alias '" + args[0].toLowerCase() + "' does not exist.");
      return;
    }

    DiscordBot.serverSettings.get(e.getGuild().getId()).aliases.remove(args[0].toLowerCase());
    DiscordBot.writeSettingsJson();
    DiscordBot.sendMessage(e.getChannel(), "Alias '" + args[0].toLowerCase() + "' has been removed.");

  }

  @Override
  public String usage(String prefix) {
    return prefix + "removeAlias [alias name]";
  }

  @Override
  public String descripition() {
    return "Removes an alias from a command.";
  }

  @Override
  public void executed(boolean success, GuildMessageReceivedEvent e) {
    return;
  }
}
