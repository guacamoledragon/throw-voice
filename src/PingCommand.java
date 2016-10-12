import net.dv8tion.jda.events.message.MessageReceivedEvent;

public class PingCommand implements Command {
    private final String HELP = "USAGE: !ping";

    @Override
    public Boolean called(String[] args, MessageReceivedEvent e){
        return true;
    }

    @Override
    public void action(String[] args, MessageReceivedEvent e) {
        e.getTextChannel().sendMessage("Pong!");
    }

    @Override
    public String help() {
        return HELP;
    }

    @Override
    public void executed(boolean success, MessageReceivedEvent e){
        return;
    }
}
