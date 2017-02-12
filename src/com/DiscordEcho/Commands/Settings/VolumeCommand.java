package com.DiscordEcho.Commands.Settings;

import com.DiscordEcho.Commands.Command;
import com.DiscordEcho.DiscordEcho;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;


public class VolumeCommand implements Command {

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

        try{
            int num = Integer.parseInt(args[0]);

            if (num > 0 && num <= 100) {
                double percent = (double) num / 100.0;
                DiscordEcho.serverSettings.get(e.getGuild().getId()).volume = percent;
                DiscordEcho.writeSettingsJson();

                DiscordEcho.sendMessage(e.getChannel(), "Volume set to " + num + "% for next recording!");

            } else {
                DiscordEcho.sendMessage(e.getChannel(), DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix + usage());
                return;
            }

        } catch (Exception ex) {
            DiscordEcho.sendMessage(e.getChannel(), DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix + usage());
            return;
        }
    }

    @Override
    public String usage() {
        return "volume [1-100]";
    }

    @Override
    public String descripition() {
        return "Sets the percentage volume to record at, from 1-100%";
    }

    @Override
    public void executed(boolean success, GuildMessageReceivedEvent e){
        return;
    }
}
