package tech.gdragon.commands;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import tech.gdragon.DiscordBot;
import tech.gdragon.configuration.ServerSettings;
import tech.gdragon.db.Shim;
import tech.gdragon.db.dao.Alias;
import tech.gdragon.db.dao.Guild;
import tech.gdragon.db.dao.Settings;

import java.util.HashMap;
import java.util.Iterator;

public class CommandHandler {
  public static final CommandParser parser = new CommandParser();
  public static HashMap<String, Command> commands = new HashMap<>();

  @Deprecated
  public static void handleCommand(CommandParser.CommandContainer cmd) {
    ServerSettings settings = DiscordBot.serverSettings.get(cmd.event.getGuild().getId());

    if (commands.containsKey(cmd.invoke.toLowerCase()) || settings.aliases.containsKey(cmd.invoke.toLowerCase())) {

      String invoke;
      if (settings.aliases.containsKey(cmd.invoke.toLowerCase())) {
        invoke = settings.aliases.get(cmd.invoke);
      } else {
        invoke = cmd.invoke;
      }

      commands.get(invoke).action(cmd.args, cmd.event);
    }
  }

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
