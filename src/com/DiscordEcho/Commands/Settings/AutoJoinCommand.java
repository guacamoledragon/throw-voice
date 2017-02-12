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
        if (args.length != 2) {
            DiscordEcho.sendMessage(e.getChannel(), DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix + usage());
            return;
        }

        int num;
        try {
            num = Integer.parseInt(args[1]);

            if (num == 0)
                num = Integer.MAX_VALUE;
            else if (num < 0)
                DiscordEcho.sendMessage(e.getChannel(), DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix + usage());

        } catch (Exception ex) {

            if (!args[1].equals("off")) {
                DiscordEcho.sendMessage(e.getChannel(), DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix + usage());
                return;

            } else {
                num = Integer.MAX_VALUE;
            }
        }


        if (args[0].toLowerCase().equals("all")) {
            ServerSettings settings = DiscordEcho.serverSettings.get(e.getGuild().getId());

            for (VoiceChannel vc : e.getGuild().getVoiceChannels()) {
                settings.autoJoinSettings.put(vc.getId(), new Integer(num));
            }
            DiscordEcho.writeSettingsJson();

            if (num != Integer.MAX_VALUE)
                DiscordEcho.sendMessage(e.getChannel(), "Will now automatically join any voice channel with " + num + " people");
            else
                DiscordEcho.sendMessage(e.getChannel(), "Will no longer automatically join any channel");


        } else {
            ServerSettings settings = DiscordEcho.serverSettings.get(e.getGuild().getId());

            if(e.getGuild().getVoiceChannelsByName(args[0], true).size() == 0) {
                DiscordEcho.sendMessage(e.getChannel(), "Cannot find specified voice channel");
                return;
            }

            settings.autoJoinSettings.put(e.getGuild().getVoiceChannelsByName(args[0], true).get(0).getId(), new Integer(num));
            DiscordEcho.writeSettingsJson();

            if (num != Integer.MAX_VALUE)
                DiscordEcho.sendMessage(e.getChannel(), "Will now automatically join " + args[0] + " when there are " + num + " people");
            else
                DiscordEcho.sendMessage(e.getChannel(), "Will no longer automatically join " + args[0]);

        }
    }

    @Override
    public String usage() {
        return "autojoin [Voice Channel name | 'all'] [number | 'off']";
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
