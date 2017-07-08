package tech.gdragon.commands.audio;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import tech.gdragon.DiscordEcho;
import tech.gdragon.commands.Command;
import tech.gdragon.listeners.AudioReceiveListener;
import tech.gdragon.listeners.AudioSendListener;


public class EchoCommand implements Command {

    @Override
    public Boolean called(String[] args, GuildMessageReceivedEvent e){
        return true;
    }

    @Override
    public void action(String[] args, GuildMessageReceivedEvent e) {
        if (args.length != 1) {
            String prefix = DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix;
            DiscordEcho.sendMessage(e.getChannel(), usage(prefix));
            return;
        }

        if(e.getGuild().getAudioManager().getConnectedChannel() == null) {
            DiscordEcho.sendMessage(e.getChannel(), "I wasn't recording!");
            return;
        }

        int time;
        try {
            time = Integer.parseInt(args[0]);
            if (time <= 0) {
                DiscordEcho.sendMessage(e.getChannel(), "Time must be greater than 0");
                return;
            }
        } catch (Exception ex) {
            String prefix = DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix;
            DiscordEcho.sendMessage(e.getChannel(), usage(prefix));
            return;
        }


        AudioReceiveListener ah = (AudioReceiveListener) e.getGuild().getAudioManager().getReceiveHandler();
        byte[] voiceData;
        if (ah == null || (voiceData = ah.getUncompVoice(time)) == null) {
            DiscordEcho.sendMessage(e.getChannel(), "I wasn't recording!");
            return;
        }

        AudioSendListener as = new AudioSendListener(voiceData);
        e.getGuild().getAudioManager().setSendingHandler(as);

    }

    @Override
    public String usage(String prefix) {
        return prefix + "echo [seconds]";
    }

    @Override
    public String descripition() {
        return "Echos back the input number of seconds of the recording into the voice channel (max 120 seconds)";
    }

    @Override
    public void executed(boolean success, GuildMessageReceivedEvent e){
        return;
    }
}
