package com.DiscordEcho;

import com.DiscordEcho.Commands.*;
import com.DiscordEcho.Commands.Audio.ClipCommand;
import com.DiscordEcho.Commands.Audio.EchoCommand;
import com.DiscordEcho.Commands.Audio.SaveCommand;
import com.DiscordEcho.Commands.Misc.HelpCommand;
import com.DiscordEcho.Commands.Misc.JoinCommand;
import com.DiscordEcho.Commands.Misc.LeaveCommand;
import com.DiscordEcho.Commands.Settings.*;
import com.DiscordEcho.Configuration.ServerSettings;
import com.DiscordEcho.Listeners.AudioReceiveListener;
import com.DiscordEcho.Listeners.AudioSendListener;
import com.DiscordEcho.Listeners.EventListener;
import com.google.gson.Gson;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.sourceforge.lame.lowlevel.LameEncoder;
import net.sourceforge.lame.mp3.Lame;
import net.sourceforge.lame.mp3.MPEGMode;

import javax.security.auth.login.LoginException;
import javax.sound.sampled.AudioFormat;
import java.awt.*;
import java.io.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.List;

import static java.lang.Thread.sleep;

public class DiscordEcho
{
    public static HashMap<String, ServerSettings> serverSettings = new HashMap<>();

    public static void main(String[] args)
    {
        try
        {
            FileReader fr = new FileReader("shark_secret");
            BufferedReader br = new BufferedReader(fr);
            String secret = br.readLine();

            JDA api = new JDABuilder(AccountType.BOT)
                    .setToken(secret)
                    .addListener(new EventListener())
                    .buildBlocking();
    }
        catch (LoginException e)
        {
            //If anything goes wrong in terms of authentication, this is the exception that will represent it
            e.printStackTrace();
        }
        catch (InterruptedException e)
        {
            //Due to the fact that buildBlocking is a blocking method, one which waits until JDA is fully loaded,
            // the waiting can be interrupted. This is the exception that would fire in that situation.
            //As a note: in this extremely simplified example this will never occur. In fact, this will never occur unless
            // you use buildBlocking in a thread that has the possibility of being interrupted (async thread usage and interrupts)
            e.printStackTrace();
        }
        catch (RateLimitedException e)
        {
            //The login process is one which can be ratelimited. If you attempt to login in multiple times, in rapid succession
            // (multiple times a second), you would hit the ratelimit, and would see this exception.
            //As a note: It is highly unlikely that you will ever see the exception here due to how infrequent login is.
            e.printStackTrace();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        CommandHandler.commands.put("help", new HelpCommand());
        CommandHandler.commands.put("autojoin", new AutoJoinCommand());
        CommandHandler.commands.put("autoleave", new AutoLeaveCommand());
        CommandHandler.commands.put("autosave", new AutoSaveCommand());
        CommandHandler.commands.put("join", new JoinCommand());
        CommandHandler.commands.put("leave", new LeaveCommand());
        CommandHandler.commands.put("symbol", new SymbolCommand());
        CommandHandler.commands.put("save", new SaveCommand());
        CommandHandler.commands.put("clip", new ClipCommand());
        CommandHandler.commands.put("volume", new VolumeCommand());
        CommandHandler.commands.put("savelocation", new SaveLocationCommand());
        CommandHandler.commands.put("echo", new EchoCommand());

    }



    //UTILITY FUNCTIONS

    public static VoiceChannel biggestChannel(List<VoiceChannel> vcs) {
        int large = 0;
        VoiceChannel biggest = null;

        for (VoiceChannel v : vcs) {
            if (voiceChannelSize(v) > large) {
                if (voiceChannelSize(v) >= DiscordEcho.serverSettings.get(v.getGuild().getId()).autoJoinSettings.get(v.getId())) {
                    biggest = v;
                    large = voiceChannelSize(v);
                }
            }
        }
        return biggest;
    }

    public static int voiceChannelSize(VoiceChannel vc) {
        if (vc == null) return 0;

        int i = 0;
        for (Member m : vc.getMembers()){
            if(!m.getUser().isBot()) i++;
        }
        return i;
    }

    public static void writeToFile(Guild guild) {
        writeToFile(guild, -1, null);
    }

    public static void writeToFile(Guild guild, TextChannel tc) {
        writeToFile(guild, -1, tc);
    }

    public static void writeToFile(Guild guild, int time, TextChannel tc) {
        if (tc == null)
            tc = guild.getTextChannelById(serverSettings.get(guild.getId()).defaultTextChannel);
        
        AudioReceiveListener ah = (AudioReceiveListener) guild.getAudioManager().getReceiveHandler();
        if (ah == null) {
            DiscordEcho.sendMessage(tc, "I wasn't recording!");
            return;
        }

        File dest;
        try {

            if (new File("/var/www/html/").exists())
                dest = new File("/var/www/html/" + getPJSaltString() + ".mp3");
            else
                dest = new File("recordings/" + getPJSaltString() + ".mp3");

            byte[] voiceData;
            ah.canReceive = false;

            if (time > 0 && time <= ah.PCM_MINS * 60 * 2) {
                voiceData = ah.getUncompVoice(time);
                voiceData = encodePcmToMp3(voiceData);

            } else {
                voiceData = ah.getVoiceData();
            }

            FileOutputStream fos = new FileOutputStream(dest);
            fos.write(voiceData);
            fos.close();

            System.out.format("Saving audio file '%s' from %s on %s of size %f MB\n",
                    dest.getName(), guild.getAudioManager().getConnectedChannel().getName(), guild.getName(), (double) dest.length() / 1024 / 1024);

            if (dest.length() / 1024 / 1024 < 8) {
                final TextChannel channel = tc;
                tc.sendFile(dest, null).queue(null, (Throwable) -> {
                    channel.sendMessage("I don't have permissions to send files here!").queue();
                });

                new Thread(() -> {
                    try { sleep(1000 * 20); } catch (Exception ex) {}    //20 second life for files set to discord (no need to save)

                    dest.delete();
                    System.out.println("\tDeleting file " + dest.getName() + "...");

                }).start();

            } else {
                DiscordEcho.sendMessage(tc, "http://com.DiscordEcho.DiscordEcho.com/" + dest.getName());

                new Thread(() -> {
                    try { sleep(1000 * 60 * 60); } catch (Exception ex) {}    //1 hour life for files stored on web server

                    dest.delete();
                    System.out.println("\tDeleting file " + dest.getName() + "...");

                }).start();
            }

        } catch (Exception ex) {
            ex.printStackTrace();

            if (tc != null)
                DiscordEcho.sendMessage(tc, "Unknown error sending file");
            else
                DiscordEcho.sendMessage(guild.getTextChannelById(serverSettings.get(guild.getId()).defaultTextChannel), "Unknown error sending file");

        }
    }


    public static void writeSettingsJson() {
        try {
            Gson gson = new Gson();
            String json = gson.toJson(DiscordEcho.serverSettings);

            FileWriter fw = new FileWriter("settings.json");
            fw.write(json);
            fw.flush();
            fw.close();

        } catch (Exception ex) {}
    }

    public static void alert(VoiceChannel vc) {
        for (Member m : vc.getMembers()) {
            if(m.getUser() == vc.getJDA().getSelfUser()) continue;
            if (!serverSettings.get(vc.getGuild().getId()).alertBlackList.contains(m.getUser().getId()) && !m.getUser().isBot()) {

                EmbedBuilder embed = new EmbedBuilder();
                embed.setAuthor("Discord Echo", "https://devpost.com/software/discord-recorder", vc.getJDA().getSelfUser().getAvatarUrl());
                embed.setColor(Color.RED);
                embed.setTitle("Your audio is now being recorded in '" + vc.getName() + "' on '" + vc.getGuild().getName() + "'");
                embed.setDescription("Disable this alert with ``!alerts off``");
                embed.setThumbnail("http://www.freeiconspng.com/uploads/alert-icon-png-red-alert-round-icon-clip-art-3.png");
                embed.setTimestamp(OffsetDateTime.now());

                m.getUser().openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage(embed.build()).queue());

            }
        }
    }


    public static String getPJSaltString() {
        String SALTCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890";
        StringBuilder salt = new StringBuilder();
        Random rnd = new Random();
        while (salt.length() < 13) {
            int index = (int) (rnd.nextFloat() * SALTCHARS.length());
            salt.append(SALTCHARS.charAt(index));
        }
        String saltStr = salt.toString();
        return saltStr;
    }

    public static byte[] encodePcmToMp3(byte[] pcm) {
        LameEncoder encoder = new LameEncoder(new AudioFormat(48000.0f, 16, 2, true, true), 128, MPEGMode.STEREO, Lame.QUALITY_HIGHEST, false);
        ByteArrayOutputStream mp3 = new ByteArrayOutputStream();
        byte[] buffer = new byte[encoder.getPCMBufferSize()];

        int bytesToTransfer = Math.min(buffer.length, pcm.length);
        int bytesWritten;
        int currentPcmPosition = 0;
        while (0 < (bytesWritten = encoder.encodeBuffer(pcm, currentPcmPosition, bytesToTransfer, buffer))) {
            currentPcmPosition += bytesToTransfer;
            bytesToTransfer = Math.min(buffer.length, pcm.length - currentPcmPosition);

            mp3.write(buffer, 0, bytesWritten);
        }

        encoder.close();

        return mp3.toByteArray();
    }

    public static void killAudioHandlers(Guild g) {
        AudioReceiveListener ah = (AudioReceiveListener) g.getAudioManager().getReceiveHandler();
        if (ah != null) {
            ah.canReceive = false;
            ah.compVoiceData = null;
            g.getAudioManager().setReceivingHandler(null);
        }

        AudioSendListener sh = (AudioSendListener) g.getAudioManager().getSendingHandler();
        if (sh != null) {
            sh.canProvide = false;
            sh.voiceData = null;
            g.getAudioManager().setSendingHandler(null);
        }

        System.out.println("Destroyed audio handlers for " + g.getName());
        System.gc();
    }

    public static void sendMessage(TextChannel tc, String message) {
        tc.sendMessage(message).queue(null, (Throwable) -> {
            tc.getGuild().getPublicChannel().sendMessage("I don't have permissions to send messages there!").queue();
        });
    }

    public static void joinVoiceChannel(VoiceChannel vc, boolean warning) {
        System.out.format("Joining '%s' voice channel in %s\n", vc.getName(), vc.getGuild().getName());

        try {
            vc.getGuild().getAudioManager().openAudioConnection(vc);
        } catch (Exception e) {
            if (warning)
                sendMessage(vc.getGuild().getPublicChannel(), "I don't have permission to join that voice channel!");
        }

        DiscordEcho.alert(vc);
        double volume = DiscordEcho.serverSettings.get(vc.getGuild().getId()).volume;
        vc.getGuild().getAudioManager().setReceivingHandler(new AudioReceiveListener(volume));

    }
    public static void leaveVoiceChannel(VoiceChannel vc) {
        System.out.format("Leaving '%s' voice channel in %s\n", vc.getGuild(), vc.getGuild().getName());

        vc.getGuild().getAudioManager().closeAudioConnection();
        DiscordEcho.killAudioHandlers(vc.getGuild());
    }
}
