package tech.gdragon.commands;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;

public interface Command {
    Boolean called(String[] args, GuildMessageReceivedEvent e);
    void action(String[] args, GuildMessageReceivedEvent e);
    String usage(String prefix);
    String descripition();
    void executed(boolean success, GuildMessageReceivedEvent e);
}
