package tech.gdragon.commands.audio;

import tech.gdragon.commands.Command;
import tech.gdragon.DiscordEcho;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;


public class ClipCommand implements Command {

    @Override
    public Boolean called(String[] args, GuildMessageReceivedEvent e){
        return true;
    }

    @Override
    public void action(String[] args, GuildMessageReceivedEvent e) {
        if (args.length != 1 && args.length != 2) {
            DiscordEcho.sendMessage(e.getChannel(), DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix + usage(DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix));
            return;
        }

        if(e.getGuild().getAudioManager().getConnectedChannel() == null) {
            DiscordEcho.sendMessage(e.getChannel(), "I wasn't recording!");
            return;
        }

        //cut off # in channel name if they included it
        if (args.length == 2 && args[1].startsWith("#")) {
            args[1] = args[1].substring(1);
        }

        if (args.length == 2 && e.getGuild().getTextChannelsByName(args[1], true).size() == 0) {
            DiscordEcho.sendMessage(e.getChannel(), "Cannot find specified text channel");
            return;
        }

        int time;
        try {
            time = Integer.parseInt(args[0]);
        } catch (Exception ex) {
            String prefix = DiscordEcho.serverSettings.get(e.getGuild().getId()).prefix;
            DiscordEcho.sendMessage(e.getChannel(), usage(prefix));
            return;
        }

        if (time <= 0) {
            DiscordEcho.sendMessage(e.getChannel(), "Time must be greater than 0");
            return;
        }

        if (args.length == 2)
            DiscordEcho.writeToFile(e.getGuild(), time, e.getGuild().getTextChannelsByName(args[1], true).get(0));
        else
            DiscordEcho.writeToFile(e.getGuild(), time, e.getChannel());

    }

    @Override
    public String usage(String prefix) {
        return prefix + "clip [seconds] | clip [seconds] [text channel output]";
    }

    @Override
    public String descripition() {
        return "Saves a clip of the specified length and outputs it in the current or specified text channel (max 120 seconds)";
    }

    @Override
    public void executed(boolean success, GuildMessageReceivedEvent e){
        return;
    }
}
