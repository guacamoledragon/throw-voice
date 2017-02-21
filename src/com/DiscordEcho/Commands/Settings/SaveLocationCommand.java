package com.DiscordEcho.Commands.Settings;

import com.DiscordEcho.Commands.Command;
import com.DiscordEcho.DiscordEcho;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;


public class SaveLocationCommand implements Command {
    
    @Override
    public Boolean called(String[] args, GuildMessageReceivedEvent e){
        return true;
    }

    @Override
    public void action(String[] args, GuildMessageReceivedEvent e) {
        if (args.length > 1) {
            String prefix = DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix;
            DiscordEcho.sendMessage(e.getChannel(), usage(prefix));
            return;
        }

        if (args.length == 0) {
            String id = e.getChannel().getId();
            DiscordEcho.serverSettings.get(e.getGuild().getId()).defaultTextChannel = id;
            DiscordEcho.sendMessage(e.getChannel(), "Now defaulting to the " + e.getChannel().getName() + " text channel");
            DiscordEcho.writeSettingsJson();

        } else if (args.length == 1) {
            if(e.getGuild().getTextChannelsByName(args[0], true).size() == 0) {
                DiscordEcho.sendMessage(e.getChannel(), "Cannot find specified voice channel");
                return;
            }
            String id = e.getGuild().getTextChannelsByName(args[0], true).get(0).getId();
            DiscordEcho.serverSettings.get(e.getGuild().getId()).defaultTextChannel = id;
            DiscordEcho.sendMessage(e.getChannel(), "Now defaulting to the " + e.getGuild().getTextChannelById(id).getName() + " text channel");
            DiscordEcho.writeSettingsJson();

        }
    }

    @Override
    public String usage(String prefix) {
        return prefix + "saveLocation | " + prefix + "saveLocation [text channel name]";
    }

    @Override
    public String descripition() {
        return "Sets the text channel of message or the text channel specified as the default location to send files";
    }

    @Override
    public void executed(boolean success, GuildMessageReceivedEvent e){
        return;
    }
}
