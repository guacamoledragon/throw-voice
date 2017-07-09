package tech.gdragon.commands.misc;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import tech.gdragon.DiscordBot;
import tech.gdragon.commands.Command;


public class LeaveCommand implements Command {

  @Override
  public Boolean called(String[] args, GuildMessageReceivedEvent e) {
    return true;
  }

  @Override
  public void action(String[] args, GuildMessageReceivedEvent e) {
    if (args.length != 0) {
      String prefix = DiscordBot.serverSettings.get(e.getGuild().getId()).prefix;
      DiscordBot.sendMessage(e.getChannel(), usage(prefix));
      return;
    }

    if (!e.getGuild().getAudioManager().isConnected()) {
      DiscordBot.sendMessage(e.getChannel(), "I am not in a channel!");
      return;
    }

    //write out previous channel's audio if autoSave is on
    if (DiscordBot.serverSettings.get(e.getGuild().getId()).autoSave)
      DiscordBot.writeToFile(e.getGuild());

    DiscordBot.leaveVoiceChannel(e.getGuild().getAudioManager().getConnectedChannel());

  }

  @Override
  public String usage(String prefix) {
    return prefix + "leave";
  }

  @Override
  public String descripition() {
    return "Force the bot to leave it's current channel";
  }

  @Override
  public void executed(boolean success, GuildMessageReceivedEvent e) {
    return;
  }
}
