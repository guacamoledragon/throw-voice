package com.DiscordEcho.Commands;

import java.util.HashMap;

public class CommandHandler {
    public static final CommandParser parser = new CommandParser();
    public static HashMap<String, Command> commands = new HashMap<>();
    public static HashMap<String, String> aliases = new HashMap<>();

    public static void handleCommand(CommandParser.CommandContainer cmd){
        if (commands.containsKey(cmd.invoke) || aliases.containsKey(cmd.invoke)) {
            
            String invoke;
            if (aliases.containsKey(cmd.invoke))
                invoke = aliases.get(cmd.invoke);
            else
                invoke = cmd.invoke;
                
            Boolean safe = commands.get(invoke).called(cmd.args, cmd.e);

            if (safe) {
                commands.get(invoke).action(cmd.args,cmd.e);
                commands.get(invoke).executed(safe,cmd.e);

            }
            else {
                commands.get(invoke).executed(safe,cmd.e);
            }
        }
    }
}
