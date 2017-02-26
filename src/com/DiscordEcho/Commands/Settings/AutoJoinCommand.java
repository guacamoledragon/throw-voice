package com.DiscordEcho.Commands.Settings;

import com.DiscordEcho.Commands.Command;
import com.DiscordEcho.Configuration.ServerSettings;
import com.DiscordEcho.DiscordEcho;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;


public class AutoJoinCommand implements Command {
    
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

            if (num == 0)
                num = Integer.MAX_VALUE;
            else if (num < 0) {
                String prefix = DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix;
                DiscordEcho.sendMessage(e.getChannel(), "Number must be positive!");
                return;
            }

        } catch (Exception ex) {

            if (!args[args.length - 1].equals("off")) {
                String prefix = DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix;
                DiscordEcho.sendMessage(e.getChannel(), usage(prefix));
                return;

            } else {
                num = Integer.MAX_VALUE;
            }
        }

        ServerSettings settings = DiscordEcho.serverSettings.get(e.getGuild().getId());

        if (args[0].toLowerCase().equals("all") && args.length == 2) {

            for (VoiceChannel vc : e.getGuild().getVoiceChannels()) {
                settings.autoJoinSettings.put(vc.getId(), new Integer(num));
            }
            DiscordEcho.writeSettingsJson();

            if (num != Integer.MAX_VALUE)
                DiscordEcho.sendMessage(e.getChannel(), "Will now automatically join any voice channel with " + num + " people");
            else
                DiscordEcho.sendMessage(e.getChannel(), "Will no longer automatically join any channel");

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

            settings.autoJoinSettings.put(e.getGuild().getVoiceChannelsByName(name, true).get(0).getId(), new Integer(num));
            DiscordEcho.writeSettingsJson();

            if (num != Integer.MAX_VALUE)
                DiscordEcho.sendMessage(e.getChannel(), "Will now automatically join '" + name + "' when there are " + num + " people");
            else
                DiscordEcho.sendMessage(e.getChannel(), "Will no longer automatically join '" + name + "'.");

        }
    }

    @Override
    public String usage(String prefix) {
        return prefix + "autojoin [Voice Channel name | 'all'] [number | 'off']";
    }

    @Override
    public String descripition() {
        return "Sets the number of players for the bot to auto-join a voice channel, or disables auto-joining. All will apply number to all voice channels.";
    }

    @Override
    public void executed(boolean success, GuildMessageReceivedEvent e){
        return;
    }
}
