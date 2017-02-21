package com.DiscordEcho.Commands.Audio;

import com.DiscordEcho.Commands.Command;
import com.DiscordEcho.DiscordEcho;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;


public class SaveCommand implements Command {

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

        if(e.getGuild().getAudioManager().getConnectedChannel() == null) {
            DiscordEcho.sendMessage(e.getChannel(), "I wasn't recording!");
            return;
        }

        if (args.length == 0)
            DiscordEcho.writeToFile(e.getGuild(), e.getChannel());

        else if (args.length == 1) {
            if (e.getGuild().getTextChannelsByName(args[0], true).size() == 0) {
                DiscordEcho.sendMessage(e.getChannel(), "Cannot find specified voice channel");
                return;
            }
            DiscordEcho.writeToFile(e.getGuild(), e.getGuild().getTextChannelsByName(args[0], true).get(0));
        }
    }

    @Override
    public String usage(String prefix) {
        return prefix + "save | " + prefix + "save [text channel output]";
    }

    @Override
    public String descripition() {
        return "Saves the current recording and outputs it to the current or specified text chats (caps at 16MB)";
    }

    @Override
    public void executed(boolean success, GuildMessageReceivedEvent e){
        return;
    }
}
