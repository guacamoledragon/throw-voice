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
        if (args.length != 2) {
            String prefix = DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix;
            DiscordEcho.sendMessage(e.getChannel(), usage(prefix));
            return;
        }

        int num;
        try {
            num = Integer.parseInt(args[1]);

        } catch (Exception ex) {

            if (!args[1].equals("off")) {
                String prefix = DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix;
                DiscordEcho.sendMessage(e.getChannel(), usage(prefix));
                return;

            } else {
                num = -1;
            }
        }

        if (args[0].toLowerCase().equals("all")) {
            ServerSettings settings = DiscordEcho.serverSettings.get(e.getGuild().getId());

            for (VoiceChannel vc : e.getGuild().getVoiceChannels()) {
                settings.autoLeaveSettings.put(vc.getId(), new Integer(num));
            }
            DiscordEcho.writeSettingsJson();

            if (num != -1)
                DiscordEcho.sendMessage(e.getChannel(), "Will now automatically leave any voice channel with " + num + " people");
            else
                DiscordEcho.sendMessage(e.getChannel(), "Will no longer automatically leave any channel");


        } else {
            ServerSettings settings = DiscordEcho.serverSettings.get(e.getGuild().getId());

            if(e.getGuild().getVoiceChannelsByName(args[0], true).size() == 0) {
                DiscordEcho.sendMessage(e.getChannel(), "Cannot find specified voice channel");
                return;
            }

            settings.autoLeaveSettings.put(e.getGuild().getVoiceChannelsByName(args[0], true).get(0).getId(), new Integer(num));
            DiscordEcho.writeSettingsJson();

            if (num != -1)
                DiscordEcho.sendMessage(e.getChannel(), "Will now automatically leave " + args[0] + " when there are " + num + " people");
            else
                DiscordEcho.sendMessage(e.getChannel(), "Will no longer automatically leave " + args[0]);

        }
    }

    @Override
    public String usage(String prefix) {
        return prefix + "autojoin [Voice Channel name | 'all'] [number | 'off']";
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
