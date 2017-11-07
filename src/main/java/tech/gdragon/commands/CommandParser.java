package tech.gdragon.commands;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;

import java.util.ArrayList;
import java.util.Collections;

public class CommandParser {
  public CommandContainer parse(String raw, GuildMessageReceivedEvent event) {
    ArrayList<String> split = new ArrayList<>();
    String beheaded = raw.substring(1);
    String[] splitBeheaded = beheaded.split(" ");
    Collections.addAll(split, splitBeheaded);
    String invoke = split.get(0);
    String[] args = new String[split.size() - 1];
    split.subList(1, split.size()).toArray(args);

    return new CommandContainer(raw, beheaded, splitBeheaded, invoke, args, event);
  }

  public class CommandContainer {
    public final String raw;
    public final String beheaded;
    public final String[] splitBeheaded;
    public final String invoke;
    public final String[] args;
    public final GuildMessageReceivedEvent event;

    public CommandContainer(String raw, String beheaded, String[] splitBeheaded, String invoke, String[] args, GuildMessageReceivedEvent event) {

      this.raw = raw;
      this.beheaded = beheaded;
      this.splitBeheaded = splitBeheaded;
      this.invoke = invoke;
      this.args = args;
      this.event = event;
    }
  }
}
