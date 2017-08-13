package tech.gdragon.commands;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;

public interface Command {
  /**
   * Perform the Command action
   */
  void action(String[] args, GuildMessageReceivedEvent e);

  String usage(String prefix);

  String description();
}
