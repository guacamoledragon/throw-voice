import net.dv8tion.jda.events.message.MessageReceivedEvent;

public interface Command {
    Boolean called(String[] args, MessageReceivedEvent e);
    void action(String[] args, MessageReceivedEvent e);
    String help();
    void executed(boolean success, MessageReceivedEvent e);
}
