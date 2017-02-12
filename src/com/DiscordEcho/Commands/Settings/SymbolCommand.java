package com.DiscordEcho.Commands.Settings;

import com.DiscordEcho.Commands.Command;
import com.DiscordEcho.DiscordEcho;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;


public class SymbolCommand implements Command {

    @Override
    public Boolean called(String[] args, GuildMessageReceivedEvent e){
        return true;
    }

    @Override
    public void action(String[] args, GuildMessageReceivedEvent e) {
        if (args[0].length() != 1 || args.length != 1) {
            DiscordEcho.sendMessage(e.getChannel(), DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix + usage());
            return;
        }

        DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix = args[0];
        DiscordEcho.writeSettingsJson();

        DiscordEcho.sendMessage(e.getChannel(), "Command prefix now set to " + args[0]);
    }

    @Override
    public String usage() {
        return "symbol [character]";
    }

    @Override
    public String descripition() {
        return "Sets the prefix for each command to avoid conflict with other bots (Default is '!')";
    }

    @Override
    public void executed(boolean success, GuildMessageReceivedEvent e){
        return;
    }
}
