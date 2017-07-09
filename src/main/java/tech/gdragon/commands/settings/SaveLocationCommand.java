package tech.gdragon.commands.settings;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import tech.gdragon.DiscordBot;
import tech.gdragon.commands.Command;


public class SaveLocationCommand implements Command {

  @Override
  public Boolean called(String[] args, GuildMessageReceivedEvent e) {
    return true;
  }

  @Override
  public void action(String[] args, GuildMessageReceivedEvent e) {
    if (args.length > 1) {
      String prefix = DiscordBot.serverSettings.get(e.getGuild().getId()).prefix;
      DiscordBot.sendMessage(e.getChannel(), usage(prefix));
      return;
    }

    if (args.length == 0) {
      String id = e.getChannel().getId();
      DiscordBot.serverSettings.get(e.getGuild().getId()).defaultTextChannel = id;
      DiscordBot.sendMessage(e.getChannel(), "Now defaulting to the " + e.getChannel().getName() + " text channel");
      DiscordBot.writeSettingsJson();

    } else if (args.length == 1) {

      //cut off # in channel name if they included it
      if (args[0].startsWith("#")) {
        args[0] = args[0].substring(1);
      }

      if (e.getGuild().getTextChannelsByName(args[0], true).size() == 0) {
        DiscordBot.sendMessage(e.getChannel(), "Cannot find specified text channel");
        return;
      }
      String id = e.getGuild().getTextChannelsByName(args[0], true).get(0).getId();
      DiscordBot.serverSettings.get(e.getGuild().getId()).defaultTextChannel = id;
      DiscordBot.sendMessage(e.getChannel(), "Now defaulting to the " + e.getGuild().getTextChannelById(id).getName() + " text channel");
      DiscordBot.writeSettingsJson();

    }
  }

  @Override
  public String usage(String prefix) {
    return prefix + "saveLocation | " + prefix + "saveLocation [text channel name]";
  }

  @Override
  public String descripition() {
    return "Sets the text channel of message or the text channel specified as the default location to send files";
  }

  @Override
  public void executed(boolean success, GuildMessageReceivedEvent e) {
    return;
  }
}
