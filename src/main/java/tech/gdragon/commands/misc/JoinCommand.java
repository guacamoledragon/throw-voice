package tech.gdragon.commands.misc;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import tech.gdragon.DiscordBot;
import tech.gdragon.commands.Command;


public class JoinCommand implements Command {

  @Override
  public void action(String[] args, GuildMessageReceivedEvent e) {
    if (args.length != 0) {


      return;
    }

    if (e.getGuild().getAudioManager().getConnectedChannel() != null &&
      e.getGuild().getAudioManager().getConnectedChannel().getMembers().contains(e.getMember())) {
      DiscordBot.sendMessage(e.getChannel(), "I am already in your channel!");
      return;
    }

    if (e.getMember().getVoiceState().getChannel() == null) {
      DiscordBot.sendMessage(e.getChannel(), "You need to be in a voice channel to use this command!");
      return;
    }

    //write out previous channel's audio if autoSave is on
    if (e.getGuild().getAudioManager().isConnected() && DiscordBot.serverSettings.get(e.getGuild().getId()).autoSave)
      DiscordBot.writeToFile(e.getGuild());

    DiscordBot.joinVoiceChannel(e.getMember().getVoiceState().getChannel(), true);
  }

  @Override
  public String usage(String prefix) {
    return prefix + "join";
  }

  @Override
  public String description() {
    return "Force the bot to join and record your current channel";
  }
}
