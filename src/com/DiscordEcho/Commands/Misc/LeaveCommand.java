package com.DiscordEcho.Commands.Misc;

import com.DiscordEcho.Commands.Command;
import com.DiscordEcho.DiscordEcho;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;


public class LeaveCommand implements Command {

    @Override
    public Boolean called(String[] args, GuildMessageReceivedEvent e){
        return true;
    }

    @Override
    public void action(String[] args, GuildMessageReceivedEvent e) {
        if (args.length != 0) {
            DiscordEcho.sendMessage(e.getChannel(), DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix + usage());
            return;
        }

        if (!e.getGuild().getAudioManager().isConnected()) {
            DiscordEcho.sendMessage(e.getChannel(), "I am not in a channel!");
            return;
        }

        //write out previous channel's audio if autoSave is on
        if (DiscordEcho.serverSettings.get(e.getGuild().getId()).autoSave)
            DiscordEcho.writeToFile(e.getGuild());

        DiscordEcho.leaveVoiceChannel(e.getGuild().getAudioManager().getConnectedChannel());

    }

    @Override
    public String usage() {
        return "leave";
    }

    @Override
    public String descripition() {
        return "Force the bot to leave it's current channel";
    }

    @Override
    public void executed(boolean success, GuildMessageReceivedEvent e){
        return;
    }
}
