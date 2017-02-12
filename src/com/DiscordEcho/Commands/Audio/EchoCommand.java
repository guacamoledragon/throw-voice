package com.DiscordEcho.Commands.Audio;

import com.DiscordEcho.Commands.Command;
import com.DiscordEcho.DiscordEcho;
import com.DiscordEcho.Listeners.AudioReceiveListener;
import com.DiscordEcho.Listeners.AudioSendListener;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;


public class EchoCommand implements Command {

    @Override
    public Boolean called(String[] args, GuildMessageReceivedEvent e){
        return true;
    }

    @Override
    public void action(String[] args, GuildMessageReceivedEvent e) {
        if (args.length != 1) {
            DiscordEcho.sendMessage(e.getChannel(), DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix + usage());
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
            DiscordEcho.sendMessage(e.getChannel(), DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix + usage());
            return;
        }


        AudioReceiveListener ah = (AudioReceiveListener) e.getGuild().getAudioManager().getReceiveHandler();
        ah.canReceive = false;
        byte[] voiceData;
        if (ah == null || (voiceData = ah.getUncompVoice(time)) == null) {
            DiscordEcho.sendMessage(e.getChannel(), "I wasn't recording!");
            return;
        }

        AudioSendListener as = new AudioSendListener(voiceData);
        e.getGuild().getAudioManager().setSendingHandler(as);

    }

    @Override
    public String usage() {
        return "echo [seconds]";
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
