package com.DiscordEcho.Commands.Settings;

import com.DiscordEcho.Commands.Command;
import com.DiscordEcho.Configuration.ServerSettings;
import com.DiscordEcho.DiscordEcho;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;


public class AutoLeaveCommand implements Command {

    @Override
    public Boolean called(String[] args, GuildMessageReceivedEvent e){
        return true;
    }

    @Override
    public void action(String[] args, GuildMessageReceivedEvent e) {
        if (args.length < 2) {
            String prefix = DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix;
            DiscordEcho.sendMessage(e.getChannel(), usage(prefix));
            return;
        }

        int num;
        try {
            num = Integer.parseInt(args[args.length - 1]);

            if (num <= 0) {
                DiscordEcho.sendMessage(e.getChannel(), "Number must be greater than 0!");
                return;
            }
        } catch (Exception ex) {
            String prefix = DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix;
            DiscordEcho.sendMessage(e.getChannel(), usage(prefix));
            return;
        }

        ServerSettings settings = DiscordEcho.serverSettings.get(e.getGuild().getId());
        
        if (args[0].toLowerCase().equals("all") && args.length == 2) {
            
            for (VoiceChannel vc : e.getGuild().getVoiceChannels()) {
                settings.autoLeaveSettings.put(vc.getId(), new Integer(num));
            }
            DiscordEcho.writeSettingsJson();

            if (num != -1)
                DiscordEcho.sendMessage(e.getChannel(), "Will now automatically leave any voice channel with " + num + " people");
            else
                DiscordEcho.sendMessage(e.getChannel(), "Will no longer automatically leave any channel");


        } else {
            String name = "";
            for (int i=0; i < args.length - 1; i++) {
                name += args[i] + " ";
            }
            name = name.substring(0, name.length() - 1);

            if(e.getGuild().getVoiceChannelsByName(name, true).size() == 0) {
                DiscordEcho.sendMessage(e.getChannel(), "Cannot find voice channel '" + name + "'.");
                return;
            }

            settings.autoLeaveSettings.put(e.getGuild().getVoiceChannelsByName(name, true).get(0).getId(), new Integer(num));
            DiscordEcho.writeSettingsJson();

            if (num != -1)
                DiscordEcho.sendMessage(e.getChannel(), "Will now automatically leave '" + name + "' when there are " + num + " people");
            else
                DiscordEcho.sendMessage(e.getChannel(), "Will no longer automatically leave '" + name + "'.");

        }
    }

    @Override
    public String usage(String prefix) {
        return prefix + "autoleave [Voice Channel name | 'all'] [number]";
    }

    @Override
    public String descripition() {
        return "Sets the number of players for the bot to auto-leave a voice channel, or disables auto-leaving.  All will apply number to all voice channels.";
    }

    @Override
    public void executed(boolean success, GuildMessageReceivedEvent e){
        return;
    }
}
