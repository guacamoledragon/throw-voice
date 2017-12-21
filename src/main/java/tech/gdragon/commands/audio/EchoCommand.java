package tech.gdragon.commands.audio;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import tech.gdragon.BotUtils;
import tech.gdragon.commands.Command;


public class EchoCommand implements Command {
  @Override
  public void action(String[] args, GuildMessageReceivedEvent e) {
    BotUtils.sendMessage(e.getChannel(), "Echo has been deprecated, contact bot author if this functionality is desired.");
    /*
    String prefix =
      Shim.INSTANCE.xaction(() -> {
        Guild guild = Guild.Companion.findById(e.getGuild().getIdLong());
        return guild != null ? guild.getSettings().getPrefix() : "!";
      });
    if (args.length != 1) {
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


    AudioReceiveListener ah = (AudioReceiveListener) e.getGuild().getAudioManager().getReceiveHandler();
    byte[] voiceData;
    if (ah == null || (voiceData = ah.getUncompVoice(time)) == null) {
      BotUtils.sendMessage(e.getChannel(), "I wasn't recording!");
      return;
    }

    AudioSendListener as = new AudioSendListener(voiceData);
    e.getGuild().getAudioManager().setSendingHandler(as);
    */
  }

  @Override
  public String usage(String prefix) {
    return prefix + "echo [seconds]";
  }

  @Override
  public String description() {
    return "Echos back the input number of seconds of the recording into the voice channel (max 120 seconds)";
  }
}
