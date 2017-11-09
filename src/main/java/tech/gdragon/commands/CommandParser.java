package tech.gdragon.commands;

import java.util.ArrayList;
import java.util.Collections;

public class CommandParser {
  public CommandContainer parse(String raw, String prefix) {
    ArrayList<String> split = new ArrayList<>();
    String beheaded = raw.substring(prefix.length());
    String[] splitBeheaded = beheaded.split(" ");
    Collections.addAll(split, splitBeheaded);
    String invoke = split.get(0);
    String[] args = new String[split.size() - 1];
    split.subList(1, split.size()).toArray(args);

    return new CommandContainer(raw, beheaded, splitBeheaded, invoke, args);
  }

  // TODO: This is just a data class, and not all of the arguments are used.
  public class CommandContainer {
    public final String raw;
    public final String beheaded;
    public final String[] splitBeheaded;
    public final String invoke;
    public final String[] args;

    public CommandContainer(String raw, String beheaded, String[] splitBeheaded, String invoke, String[] args) {

      this.raw = raw;
      this.beheaded = beheaded;
      this.splitBeheaded = splitBeheaded;
      this.invoke = invoke;
      this.args = args;
    }
  }
}
