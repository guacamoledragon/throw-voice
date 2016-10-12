import net.dv8tion.jda.entities.VoiceChannel;
import net.dv8tion.jda.events.ReadyEvent;
import net.dv8tion.jda.events.message.MessageReceivedEvent;
import net.dv8tion.jda.events.voice.VoiceJoinEvent;
import net.dv8tion.jda.events.voice.VoiceLeaveEvent;
import net.dv8tion.jda.hooks.ListenerAdapter;
import net.dv8tion.jda.managers.AudioManager;
import net.sourceforge.jaad.util.wav.WaveFileWriter;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class EventHandler extends ListenerAdapter {

    DiscordRecorder dr;
    AudioReceiveHandle recorder;

    public EventHandler( ) {
        this.dr = new DiscordRecorder();
    }

    public void onVoiceJoin(VoiceJoinEvent e) {
        if (DiscordRecorder.autoRecord == true) {
            if (e.getChannel().getUsers().size() >= 2 && e.getUser() != e.getJDA().getSelfInfo()) {
                AudioManager am = e.getGuild().getAudioManager();
                am.openAudioConnection(biggestChannel(e.getGuild().getVoiceChannels()));
                recorder = new AudioReceiveHandle();
                am.setReceivingHandler(recorder);

            }
        }
    }

    public void onVoiceLeave(VoiceLeaveEvent e) {
        if (e.getOldChannel().getUsers().size() <= 2 && e.getOldChannel().getUsers().contains(e.getJDA().getSelfInfo())) {
            e.getGuild().getAudioManager().closeAudioConnection();      //leave voice channel

            //write to file
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");
            File dest = null;
            try {
                dest = new File(String.format("C:/Discord-Recorder/%s.wav", sdf.format(new Date()).toString()));


                WaveFileWriter wfr = new WaveFileWriter(dest, 48000, 2, 16);

                byte[] b;
                while ((b = recorder.data.poll()) != null) {
                    wfr.write(b);

                }
                wfr.close();

            } catch (Exception ex) {
                ex.printStackTrace();
            }
            e.getGuild().getTextChannels().get(0).sendFile(dest, null);


            if (biggestChannel(e.getGuild().getVoiceChannels()) != null &&
                    biggestChannel(e.getGuild().getVoiceChannels()) != e.getOldChannel()) {
                AudioManager am = e.getGuild().getAudioManager();
                am.openAudioConnection(biggestChannel(e.getGuild().getVoiceChannels()));
                recorder = new AudioReceiveHandle();
                am.setReceivingHandler(recorder);
            }
        }
    }

    public void onMessageReceived(MessageReceivedEvent e){
        if (e.getMessage().getContent().startsWith("!") && e.getMessage().getAuthor().getId() != e.getJDA().getSelfInfo().getId()) {
            CommandHandler.handleCommand(CommandHandler.parser.parse(e.getMessage().getContent().toLowerCase(), e));
        }

    }

    public void onReady(ReadyEvent e){
        //check for biggest channel and record it
    }




    //UTILITY FUNCTIONS

    public VoiceChannel biggestChannel(List<VoiceChannel> vcs) {
        int large = 0;
        VoiceChannel biggest = null;
        for (VoiceChannel v : vcs) {
            if (v.getUsers().size() > large) {
                large = v.getUsers().size();
                if (large >= 2) biggest = v;
            }
        }

        return biggest;
    }
}
