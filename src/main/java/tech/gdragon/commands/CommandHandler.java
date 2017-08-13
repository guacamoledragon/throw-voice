package tech.gdragon.commands;

import tech.gdragon.DiscordBot;
import tech.gdragon.configuration.ServerSettings;

import java.util.HashMap;

public class CommandHandler {
  public static final CommandParser parser = new CommandParser();
  public static HashMap<String, Command> commands = new HashMap<>();

  public static void handleCommand(CommandParser.CommandContainer cmd) {
    ServerSettings settings = DiscordBot.serverSettings.get(cmd.e.getGuild().getId());

    if (commands.containsKey(cmd.invoke.toLowerCase()) || settings.aliases.containsKey(cmd.invoke.toLowerCase())) {

      String invoke;
      if (settings.aliases.containsKey(cmd.invoke.toLowerCase())) {
        invoke = settings.aliases.get(cmd.invoke);
      } else {
        invoke = cmd.invoke;
      }

      commands.get(invoke).action(cmd.args, cmd.e);
    }
  }
}
