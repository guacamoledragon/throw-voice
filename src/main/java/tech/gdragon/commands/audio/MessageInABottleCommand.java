package tech.gdragon.commands.audio;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import tech.gdragon.BotUtils;
import tech.gdragon.commands.Command;


public class MessageInABottleCommand implements Command {

  @Override
  public void action(String[] args, GuildMessageReceivedEvent e) {
    BotUtils.sendMessage(e.getChannel(), "MessageInABottle has been deprecated, contact bot author if this functionality is desired.");
    /*
    String prefix =
      Shim.INSTANCE.xaction(() -> {
        Guild guild = Guild.Companion.findById(e.getGuild().getIdLong());
        return guild != null ? guild.getSettings().getPrefix() : "!";
      });
    if (args.length < 2) {
//      String prefix = DiscordBot.serverSettings.get(e.getGuild().getId()).prefix;
      BotUtils.sendMessage(e.getChannel(), usage(prefix));
      return;
    }

    if (e.getGuild().getAudioManager().getConnectedChannel() == null) {
      BotUtils.sendMessage(e.getChannel(), "I wasn't recording!");
      return;
    }

    int time;
    try {
      time = Integer.parseInt(args[0]);
      if (time <= 0) {
        BotUtils.sendMessage(e.getChannel(), "Time must be greater than 0");
        return;
      }
    } catch (Exception ex) {
//      String prefix = DiscordBot.serverSettings.get(e.getGuild().getId()).prefix;
      BotUtils.sendMessage(e.getChannel(), usage(prefix));
      return;
    }

    String name = "";
    for (int i = 1; i < args.length; i++) {
      name += args[i] + " ";
    }
    name = name.substring(0, name.length() - 1);

    if (e.getGuild().getVoiceChannelsByName(name, true).size() == 0) {
      BotUtils.sendMessage(e.getChannel(), "Cannot find voice channel '" + name + "'.");
      return;
    }

    VoiceChannel originalVC = e.getGuild().getAudioManager().getConnectedChannel();
    VoiceChannel newVC = e.getGuild().getVoiceChannelsByName(name, true).get(0);

    try {
      e.getGuild().getAudioManager().openAudioConnection(newVC);
    } catch (Exception ex) {
      BotUtils.sendMessage(e.getChannel(), "I don't have permission to join " + newVC.getName() + "!");
      return;
    }

    CommandHandler.commands.get("echo").action(new String[]{args[0]}, e);

    new Thread(() -> {
      try {
        sleep(1000 * time);
      } catch (Exception ex) {
      }

      try {
        e.getGuild().getAudioManager().openAudioConnection(originalVC);
      } catch (Exception ex) {
        BotUtils.sendMessage(e.getChannel(), "I don't have permission to join " + originalVC.getName() + "!");
        return;
      }

    }).start();
    */
  }

  @Override
  public String usage(String prefix) {
    return prefix + "miab [seconds] [voice channel]";
  }

  @Override
  public String description() {
    return "Echos back the input number of seconds of the recording into the voice channel specified and then rejoins original channel (max 120 seconds)";
  }
}
