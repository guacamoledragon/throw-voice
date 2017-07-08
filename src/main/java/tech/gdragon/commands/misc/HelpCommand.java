package tech.gdragon.commands.misc;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import tech.gdragon.DiscordEcho;
import tech.gdragon.commands.Command;
import tech.gdragon.commands.CommandHandler;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;


public class HelpCommand implements Command {

  @Override
  public Boolean called(String[] args, GuildMessageReceivedEvent e) {
    return true;
  }

  @Override
  public void action(String[] args, GuildMessageReceivedEvent e) {
    if (args.length != 0) {
      String prefix = DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix;
      DiscordEcho.sendMessage(e.getChannel(), usage(prefix));
      return;
    }

    EmbedBuilder embed = new EmbedBuilder();
    embed.setAuthor("Discord Echo", "http://DiscordEcho.com/", e.getJDA().getSelfUser().getAvatarUrl());
    embed.setColor(Color.RED);
    embed.setTitle("Currently in beta, being actively developed and tested. Expect bugs.");
    embed.setDescription("Join my guild for updates - https://discord.gg/JWNFSZJ");
    embed.setThumbnail("http://www.freeiconspng.com/uploads/information-icon-5.png");
    embed.setFooter("Replace brackets [] with item specified. Vertical bar | means 'or', either side of bar is valid choice.", "http://DiscordEcho.com/");
    embed.addBlankField(false);

    Object[] cmds = CommandHandler.commands.keySet().toArray();
    Arrays.sort(cmds);
    for (Object command : cmds) {
      if (command == "help") continue;

      Command cmd = CommandHandler.commands.get(command);
      String prefix = DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix;

      ArrayList<String> aliases = new ArrayList<>();
      for (Map.Entry<String, String> entry : DiscordEcho.serverSettings.get(e.getGuild().getId()).aliases.entrySet()) {
        if (entry.getValue().equals(command))
          aliases.add(entry.getKey());
      }

      if (aliases.size() == 0)
        embed.addField(cmd.usage(prefix), cmd.descripition(), true);
      else {
        String descripition = "";
        descripition += "Aliases: ";
        for (String alias : aliases)
          descripition += "`" + alias + "`, ";

        //remove extra comma
        descripition = descripition.substring(0, descripition.lastIndexOf(','));
        descripition += ". " + cmd.descripition();
        embed.addField(cmd.usage(prefix), descripition, true);
      }
    }

    DiscordEcho.sendMessage(e.getChannel(), "Check your DM's!");

    e.getAuthor().openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage(embed.build()).queue());
  }

  @Override
  public String usage(String prefix) {
    return prefix + "help";
  }

  @Override
  public String descripition() {
    return "Shows all commands and their usages";
  }

  @Override
  public void executed(boolean success, GuildMessageReceivedEvent e) {
    return;
  }
}
