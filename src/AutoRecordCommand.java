import net.dv8tion.jda.events.message.MessageReceivedEvent;
public class AutoRecordCommand implements Command {
    private final String HELP = "USAGE: !auto <boolean>";

    @Override
    public Boolean called(String[] args, MessageReceivedEvent e){
        return true;
    }

    @Override
    public void action(String[] args, MessageReceivedEvent e) {

        if (args.length == 0){
            e.getTextChannel().sendMessage("Please indicate true or false");
        }
        else if (args.length > 1){
            e.getTextChannel().sendMessage("Too many arguments");
        }
        else {
            if (args.length == 1) {
                if ((args[0].equals("true")) || args[0].toLowerCase().equals("t")  || args[0].equals("1")){
                    DiscordRecorder.autoRecord = true;
                    e.getTextChannel().sendMessage("I will now only record whenever you join");
                } else if ((args[0].equals("false")) || args[0].toLowerCase().equals("f")  || args[0].equals("0")) {
                    DiscordRecorder.autoRecord = false;
                    e.getTextChannel().sendMessage("I will now only record when you call me");
                }
                else{
                    e.getTextChannel().sendMessage("Boolean not detected");
                }
            }
        }
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
