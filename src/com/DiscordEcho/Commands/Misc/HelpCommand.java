package com.DiscordEcho.Commands.Misc;

import com.DiscordEcho.Commands.Command;
import com.DiscordEcho.Commands.CommandHandler;
import com.DiscordEcho.DiscordEcho;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;

import java.awt.*;
import java.util.Arrays;


public class HelpCommand implements Command {

    @Override
    public Boolean called(String[] args, GuildMessageReceivedEvent e){
        return true;
    }

    @Override
    public void action(String[] args, GuildMessageReceivedEvent e) {
        String prefix = DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix;

        EmbedBuilder embed = new EmbedBuilder();
        embed.setAuthor("Discord Echo", "http://www.com.DiscordEcho.DiscordEcho.com", e.getJDA().getSelfUser().getAvatarUrl());
        embed.setColor(Color.RED);
        embed.setTitle("Currently in beta, being actively developed and tested. Expect bugs.");
        embed.setDescription("Join my guild for updates - https://discord.gg/JWNFSZJ");
        embed.setThumbnail("http://www.freeiconspng.com/uploads/information-icon-5.png");
        embed.setFooter("Replace brackets [] with item specified. Vertical bar | means 'or', either side of bar is valid choice.", "http://www.niceme.me");
        embed.addBlankField(false);

        Object[] cmds = CommandHandler.commands.keySet().toArray();
        Arrays.sort(cmds);
        for (Object command : cmds) {
            if (command == "help") continue;
            embed.addField(prefix + CommandHandler.commands.get(command).usage(), CommandHandler.commands.get(command).descripition(), true);
        }

        DiscordEcho.sendMessage(e.getChannel(), "Check your DM's!");

        e.getAuthor().openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage(embed.build()).queue());
    }

    @Override
    public String usage() {
        return "help";
    }

    @Override
    public String descripition() {
        return "Shows all commands and their usages";
    }

    @Override
    public void executed(boolean success, GuildMessageReceivedEvent e){
        return;
    }
}
