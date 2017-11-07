package tech.gdragon.commands;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import tech.gdragon.db.Shim;
import tech.gdragon.db.dao.Alias;
import tech.gdragon.db.dao.Guild;
import tech.gdragon.db.dao.Settings;

import java.util.HashMap;

public class CommandHandler {
  public static final CommandParser parser = new CommandParser();
  public static HashMap<String, Command> commands = new HashMap<>();

  // TODO this guy needs to throw exceptions all the way to DiscordBot
  public static boolean handleCommand(GuildMessageReceivedEvent event, CommandParser.CommandContainer commandContainer) {
    return
      Shim.INSTANCE.xaction(() -> {
        Boolean isSuccess = false;
        Settings ss = Guild.Companion.findById(event.getGuild().getIdLong()).getSettings();
        String command = commandContainer.invoke;

        if (!commands.containsKey(command)) {
          for (Alias alias : ss.getAliases()) {
            if (alias.getAlias().equals(command)) {
              commands.get(alias.getName()).action(commandContainer.args, event);
              isSuccess = true;
              break;
            }
          }
        } else {
          commands.get(command).action(commandContainer.args, event);
          isSuccess = true;
        }

        return isSuccess;
      });
  }
}
