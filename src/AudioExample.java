/**
 *    Copyright 2015-2016 Austin Keener & Michael Ritter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import net.dv8tion.jda.JDA;
import net.dv8tion.jda.JDABuilder;
import net.dv8tion.jda.audio.player.Player;
import net.dv8tion.jda.events.voice.VoiceLeaveEvent;
import net.dv8tion.jda.hooks.ListenerAdapter;
import net.dv8tion.jda.events.voice.VoiceJoinEvent;
import net.dv8tion.jda.managers.AudioManager;
import net.sourceforge.jaad.util.wav.WaveFileWriter;

import javax.security.auth.login.LoginException;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class AudioExample extends ListenerAdapter
{

    /**
     * This map is used as cache and contains all player instances
     */
    private Map<String,Player> players = new HashMap<>();
    RecordAudio record;

    public static void main(String[] args)
    {
        try
        {
            JDA api = new JDABuilder()
                    .setBotToken("MjM0MzgyNjYxMzUzNzM0MTQ0.Cttiww.kElfNVT7Fw2sEIm9D19VnMA9vV4")
                    .addListener(new AudioExample())
                    .buildBlocking();
        }
        catch (IllegalArgumentException e)
        {
            System.out.println("The config was not populated. Please enter a bot token.");
        }
        catch (LoginException e)
        {
            System.out.println("The provided bot token was incorrect. Please provide valid details.");
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    public void onVoiceJoin(VoiceJoinEvent e) {
        if(e.getChannel().getUsers().size() >= 2) {
            AudioManager am = e.getGuild().getAudioManager();
            am.openAudioConnection(e.getChannel());
            record = new RecordAudio();
            am.setReceivingHandler(record);

        }
    }

    public void onVoiceLeave(VoiceLeaveEvent e) {
        if(e.getOldChannel().getUsers().size() <= 2 && e.getOldChannel().getUsers().contains(e.getJDA().getSelfInfo())) {
            //TODO: check for other servers that have > 2 and need to be joined
            e.getGuild().getAudioManager().closeAudioConnection();  //make bot leave server

            try {
                File dest = new File("C:/Discord-Recorder/test.wav");
                //BufferedInputStream in = new BufferedInputStream(new ByteArrayInputStream(record.data.poll()));
                //FileOutputStream out = new FileOutputStream(dest);

                WaveFileWriter wfr = new WaveFileWriter(dest, 48000, 2, 16);

                byte[] b;
                while ((b = record.data.poll()) != null) {
                    wfr.write(b);
                }
                wfr.close();

            }catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
