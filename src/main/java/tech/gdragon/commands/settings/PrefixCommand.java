package tech.gdragon.commands.settings;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import tech.gdragon.DiscordEcho;
import tech.gdragon.commands.Command;


public class PrefixCommand implements Command {

    @Override
    public Boolean called(String[] args, GuildMessageReceivedEvent e){
        return true;
    }

    @Override
    public void action(String[] args, GuildMessageReceivedEvent e) {
        if (args[0].length() != 1 || args.length != 1) {
            String prefix = DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix;
            DiscordEcho.sendMessage(e.getChannel(), usage(prefix));
            return;
        }

        DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix = args[0];
        DiscordEcho.writeSettingsJson();

        DiscordEcho.sendMessage(e.getChannel(), "Command prefix now set to " + args[0]);
    }

    @Override
    public String usage(String prefix) {
        return prefix + "prefix [character]";
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
