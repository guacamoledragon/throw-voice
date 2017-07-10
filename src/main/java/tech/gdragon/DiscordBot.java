package tech.gdragon;

import com.google.gson.Gson;
import de.sciss.jump3r.lowlevel.LameEncoder;
import net.dv8tion.jda.core.*;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import tech.gdragon.commands.CommandHandler;
import tech.gdragon.commands.audio.ClipCommand;
import tech.gdragon.commands.audio.EchoCommand;
import tech.gdragon.commands.audio.MessageInABottleCommand;
import tech.gdragon.commands.audio.SaveCommand;
import tech.gdragon.commands.misc.HelpCommand;
import tech.gdragon.commands.misc.JoinCommand;
import tech.gdragon.commands.misc.LeaveCommand;
import tech.gdragon.commands.settings.AlertsCommand;
import tech.gdragon.commands.settings.AliasCommand;
import tech.gdragon.commands.settings.AutoJoinCommand;
import tech.gdragon.commands.settings.AutoLeaveCommand;
import tech.gdragon.commands.settings.AutoSaveCommand;
import tech.gdragon.commands.settings.PrefixCommand;
import tech.gdragon.commands.settings.RemoveAliasCommand;
import tech.gdragon.commands.settings.SaveLocationCommand;
import tech.gdragon.commands.settings.VolumeCommand;
import tech.gdragon.configuration.ServerSettings;
import tech.gdragon.listeners.AudioReceiveListener;
import tech.gdragon.listeners.AudioSendListener;
import tech.gdragon.listeners.EventListener;

import javax.security.auth.login.LoginException;
import javax.sound.sampled.AudioFormat;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import static java.lang.Thread.sleep;

public class DiscordBot {
  //contains the id of every guild that we are connected to and their corresponding ServerSettings object
  public static HashMap<String, ServerSettings> serverSettings = new HashMap<>();

  public DiscordBot(String token) {
    try {
      //read the bot's token from a file name "token" in the main directory
//      FileReader fr = new FileReader("shark_token");
//      BufferedReader br = new BufferedReader(fr);
//      String token = br.readLine();

      //create bot instance
      JDA api = new JDABuilder(AccountType.BOT)
          .setToken(token)
          .addEventListener(new EventListener())
          .buildBlocking();
    } catch (LoginException e) {
      //If anything goes wrong in terms of authentication, this is the exception that will represent it
      e.printStackTrace();
    } catch (InterruptedException e) {
      //Due to the fact that buildBlocking is a blocking method, one which waits until JDA is fully loaded,
      // the waiting can be interrupted. This is the exception that would fire in that situation.
      //As a note: in this extremely simplified example this will never occur. In fact, this will never occur unless
      // you use buildBlocking in a thread that has the possibility of being interrupted (async thread usage and interrupts)
      e.printStackTrace();
    } catch (RateLimitedException e) {
      //The login process is one which can be ratelimited. If you attempt to login in multiple times, in rapid succession
      // (multiple times a second), you would hit the ratelimit, and would see this exception.
      //As a note: It is highly unlikely that you will ever see the exception here due to how infrequent login is.
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }

    //register commands and their aliases
    CommandHandler.commands.put("help", new HelpCommand());

    CommandHandler.commands.put("join", new JoinCommand());
    CommandHandler.commands.put("leave", new LeaveCommand());

    CommandHandler.commands.put("save", new SaveCommand());
    CommandHandler.commands.put("clip", new ClipCommand());
    CommandHandler.commands.put("echo", new EchoCommand());
    CommandHandler.commands.put("miab", new MessageInABottleCommand());

    CommandHandler.commands.put("autojoin", new AutoJoinCommand());
    CommandHandler.commands.put("autoleave", new AutoLeaveCommand());

    CommandHandler.commands.put("prefix", new PrefixCommand());
    CommandHandler.commands.put("alias", new AliasCommand());
    CommandHandler.commands.put("removealias", new RemoveAliasCommand());
    CommandHandler.commands.put("volume", new VolumeCommand());
    CommandHandler.commands.put("autosave", new AutoSaveCommand());
    CommandHandler.commands.put("savelocation", new SaveLocationCommand());
    CommandHandler.commands.put("alerts", new AlertsCommand());
  }


  public static final long PERMISSIONS = Permission.getRaw(Permission.MESSAGE_READ, Permission.MESSAGE_WRITE, Permission.VOICE_CONNECT, Permission.VOICE_USE_VAD, Permission.VOICE_SPEAK, Permission.MESSAGE_ATTACH_FILES);

  //UTILITY FUNCTIONS

  //find the biggest voice channel that surpases the server's autojoin minimum
  public static VoiceChannel biggestChannel(List<VoiceChannel> vcs) {
    int large = 0;
    VoiceChannel biggest = null;

    for (VoiceChannel v : vcs) {
      //does current interation beat old biggest?
      if (voiceChannelSize(v) > large) {
        ServerSettings settings = serverSettings.get(v.getGuild().getId());

        //we only want servers that beat the autojoin minimum (so we don't have to check later)
        if (voiceChannelSize(v) >= settings.autoJoinSettings.get(v.getId())) {
          biggest = v;
          large = voiceChannelSize(v);
        }
      }
    }
    return biggest;
  }

  //returns the effective size of the voice channel (bots don't count)
  public static int voiceChannelSize(VoiceChannel vc) {
    if (vc == null) {
      return 0;
    }

    int i = 0;
    for (Member m : vc.getMembers()) {
      if (!m.getUser().isBot()) {
        i++;
      }
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
    if (tc == null) {
      tc = guild.getTextChannelById(serverSettings.get(guild.getId()).defaultTextChannel);
    }

    AudioReceiveListener ah = (AudioReceiveListener) guild.getAudioManager().getReceiveHandler();
    if (ah == null) {
      sendMessage(tc, "I wasn't recording!");
      return;
    }

    File dest;
    try {

      if (new File("/var/www/html/").exists()) {
        dest = new File("/var/www/html/" + getPJSaltString() + ".mp3");
      } else {
        dest = new File("recordings/" + getPJSaltString() + ".mp3");
      }

      byte[] voiceData;

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
          sendMessage(guild.getTextChannelById(serverSettings.get(guild.getId()).defaultTextChannel),
            "I don't have permissions to send files in " + channel.getName() + "!");
        });

        new Thread(() -> {
          try {
            sleep(1000 * 20);
          } catch (Exception ex) {
          }    //20 second life for files set to discord (no need to save)

          dest.delete();
          System.out.println("\tDeleting file " + dest.getName() + "...");

        }).start();

      } else {
        sendMessage(tc, "http://DiscordEcho.com/" + dest.getName());

        new Thread(() -> {
          try {
            sleep(1000 * 60 * 60);
          } catch (Exception ex) {
          }    //1 hour life for files stored on web server

          dest.delete();
          System.out.println("\tDeleting file " + dest.getName() + "...");

        }).start();
      }

    } catch (Exception ex) {
      ex.printStackTrace();
      sendMessage(tc, "Unknown error sending file");
    }
  }

  //write the current state of all server settings to the settings.json file
  public static void writeSettingsJson() {
    try {
      Gson gson = new Gson();
      String json = gson.toJson(DiscordBot.serverSettings);

      FileWriter fw = new FileWriter("settings.json");
      fw.write(json);
      fw.flush();
      fw.close();

    } catch (Exception ex) {
    }
  }

  //sends alert DM to anyone in the given voicechannel who isn't on the blacklist
  public static void alert(VoiceChannel vc) {
    for (Member m : vc.getMembers()) {
      //ignore bots
      if (m.getUser().isBot()) continue;

      //check the guild's blacklist and ignore the user if they are on it
      if (!serverSettings.get(vc.getGuild().getId()).alertBlackList.contains(m.getUser().getId())) {

        //make an embeded alert message to warn the user
        EmbedBuilder embed = new EmbedBuilder();
        embed.setAuthor("Discord Echo", "https://devpost.com/software/discord-recorder", vc.getJDA().getSelfUser().getAvatarUrl());
        embed.setColor(Color.RED);
        embed.setTitle("Your audio is now being recorded in '" + vc.getName() + "' on '" + vc.getGuild().getName() + "'");
        embed.setDescription("Disable this alert with `!alerts off`");
        embed.setThumbnail("http://www.freeiconspng.com/uploads/alert-icon-png-red-alert-round-icon-clip-art-3.png");
        embed.setTimestamp(OffsetDateTime.now());

        //open private channel with the user and send the embeded message
        m.getUser().openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage(embed.build()).queue());

      }
    }
  }

  //generate a random string of 13 length with a namespace of around 2e23
  public static String getPJSaltString() {
    String SALTCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890";
    StringBuilder salt = new StringBuilder();
    Random rnd = new Random();
    while (salt.length() < 13) {
      int index = (int) (rnd.nextFloat() * SALTCHARS.length());
      salt.append(SALTCHARS.charAt(index));
    }
    String saltStr = salt.toString();

    //check for a collision on the 1/2e23 chance that it matches another salt string (lul)
    File dir = new File("/var/www/html/");
    if (!dir.exists()) {
      dir = new File("recordings/");
    }

    for (File f : dir.listFiles()) {
      if (f.getName().equals(saltStr)) {
        saltStr = getPJSaltString();
      }
    }

    return saltStr;
  }

  //encode the passed array of PCM (uncompressed) audio to mp3 audio data
  public static byte[] encodePcmToMp3(byte[] pcm) {
    LameEncoder encoder = new LameEncoder(new AudioFormat(48000.0f, 16, 2, true, true), 128, LameEncoder.CHANNEL_MODE_AUTO, LameEncoder.QUALITY_HIGHEST, false);
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

  //kill off the audio handlers and clear their memory for the given guild
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

  //general purpose function that sends a message to the given text channel and handles errors
  public static void sendMessage(TextChannel tc, String message) {
    tc.sendMessage("\u200B" + message).queue(null, (Throwable) -> {
      tc.getGuild().getPublicChannel().sendMessage("\u200BI don't have permissions to send messages in " + tc.getName() + "!").queue();
    });
  }

  //general purpose function for joining voice channels while warning and handling errors
  public static void joinVoiceChannel(VoiceChannel vc, boolean warning) {
    System.out.format("Joining '%s' voice channel in %s\n", vc.getName(), vc.getGuild().getName());

    //don't join afk channels
    if (vc == vc.getGuild().getAfkChannel()) {
      if (warning) {
        TextChannel tc = vc.getGuild().getTextChannelById(serverSettings.get(vc.getGuild().getId()).defaultTextChannel);
        sendMessage(tc, "I don't join afk channels!");
      }
    }

    //attempt to join channel and warn if permission is not available
    try {
      vc.getGuild().getAudioManager().openAudioConnection(vc);
    } catch (Exception e) {
      if (warning) {
        TextChannel tc = vc.getGuild().getTextChannelById(serverSettings.get(vc.getGuild().getId()).defaultTextChannel);
        sendMessage(tc, "I don't have permission to join " + vc.getName() + "!");
        return;
      }
    }

    //send alert to correct users in the voice channel
    DiscordBot.alert(vc);

    //initalize the audio reciever listener
    double volume = DiscordBot.serverSettings.get(vc.getGuild().getId()).volume;
    vc.getGuild().getAudioManager().setReceivingHandler(new AudioReceiveListener(volume, vc));

  }

  //general purpose function for leaving voice channels
  public static void leaveVoiceChannel(VoiceChannel vc) {
    System.out.format("Leaving '%s' voice channel in %s\n", vc.getName(), vc.getGuild().getName());

    vc.getGuild().getAudioManager().closeAudioConnection();
    DiscordBot.killAudioHandlers(vc.getGuild());
  }
}
