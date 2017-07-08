package tech.gdragon.commands.settings;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import tech.gdragon.DiscordEcho;
import tech.gdragon.commands.Command;


public class AutoSaveCommand implements Command {

  @Override
  public Boolean called(String[] args, GuildMessageReceivedEvent e) {
    return true;
  }

  @Override
  public void action(String[] args, GuildMessageReceivedEvent e) {
    if (args.length != 0) {
      String prefix = DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix;
      DiscordEcho.sendMessage(e.getChannel(), usage(prefix));
      return;
    }

    if (DiscordEcho.serverSettings.get(e.getGuild().getId()).autoSave) {
      DiscordEcho.serverSettings.get(e.getGuild().getId()).autoSave = false;
      DiscordEcho.sendMessage(e.getChannel(), "No longer saving at the end of each session!");

    } else {
      DiscordEcho.serverSettings.get(e.getGuild().getId()).autoSave = true;
      DiscordEcho.sendMessage(e.getChannel(), "Now saving at the end of each session!");
    }
  }

  @Override
  public String usage(String prefix) {
    return prefix + "autosave";
  }

  @Override
  public String descripition() {
    return "Toggles the option to automatically save and send all files at the end of each session - not just saved or clipped files";
  }

  @Override
  public void executed(boolean success, GuildMessageReceivedEvent e) {
    return;
  }
}
