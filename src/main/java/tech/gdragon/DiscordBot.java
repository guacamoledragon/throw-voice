package tech.gdragon;

import de.sciss.jump3r.lowlevel.LameEncoder;
import net.dv8tion.jda.core.*;
import net.dv8tion.jda.core.audio.AudioReceiveHandler;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.gdragon.commands.CommandHandler;
import tech.gdragon.commands.audio.ClipCommand;
import tech.gdragon.commands.audio.EchoCommand;
import tech.gdragon.commands.audio.MessageInABottleCommand;
import tech.gdragon.commands.audio.Save;
import tech.gdragon.commands.misc.Help;
import tech.gdragon.commands.misc.Join;
import tech.gdragon.commands.misc.Leave;
import tech.gdragon.commands.settings.*;
import tech.gdragon.db.Shim;
import tech.gdragon.db.dao.Settings;
import tech.gdragon.listener.CombinedAudioRecorderHandler;
import tech.gdragon.listeners.AudioReceiveListener;
import tech.gdragon.listeners.AudioSendListener;
import tech.gdragon.listeners.EventListener;

import javax.security.auth.login.LoginException;
import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Random;

import static java.lang.Thread.sleep;

public class DiscordBot {
  private static Logger logger = LoggerFactory.getLogger(DiscordBot.class);

  public DiscordBot(String token) {
    try {
      //create bot instance
      JDA api = new JDABuilder(AccountType.BOT)
        .setToken(token)
        .addEventListener(new EventListener())
        .buildBlocking();

      // Register misc commands
      CommandHandler.commands.put("help", new Help());
      CommandHandler.commands.put("join", new Join());
      CommandHandler.commands.put("leave", new Leave());

      // Register audio commands
      CommandHandler.commands.put("clip", new ClipCommand());
      CommandHandler.commands.put("echo", new EchoCommand());
      CommandHandler.commands.put("miab", new MessageInABottleCommand());
      CommandHandler.commands.put("save", new Save());

      // Register settings commands
      CommandHandler.commands.put("alias", new Alias());
      CommandHandler.commands.put("alerts", new Alerts());
      CommandHandler.commands.put("autojoin", new AutoJoin());
      CommandHandler.commands.put("autoleave", new AutoLeave());
      CommandHandler.commands.put("autosave", new AutoSave());
      CommandHandler.commands.put("prefix", new Prefix());
      CommandHandler.commands.put("removealias", new RemoveAlias());
      CommandHandler.commands.put("savelocation", new SaveLocation());
      CommandHandler.commands.put("volume", new Volume());
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
  }

  public static final long PERMISSIONS = Permission.getRaw(Permission.MESSAGE_READ, Permission.MESSAGE_WRITE, Permission.VOICE_CONNECT, Permission.VOICE_USE_VAD, Permission.VOICE_SPEAK, Permission.MESSAGE_ATTACH_FILES);

  //UTILITY FUNCTIONS

  public static void writeToFile(Guild guild, TextChannel tc, AudioReceiveHandler ah) {
    writeToFile(guild, -1, tc, ah);
  }

  public static void writeToFile(Guild guild, int time, TextChannel textChannel, AudioReceiveHandler ah) {
    Long defaultChannelId = Shim.INSTANCE.xaction(() -> {
      Settings settings = tech.gdragon.db.dao.Guild.Companion.findById(guild.getIdLong()).getSettings();
      return settings.getDefaultTextChannel();
    });

    if (textChannel == null) {
      textChannel = guild.getTextChannelById(defaultChannelId);
    }

    CombinedAudioRecorderHandler receiveListener = (CombinedAudioRecorderHandler) ah;
    if (receiveListener == null) {
      BotUtils.sendMessage(textChannel, "I wasn't recording!");
      return;
    }

    File dest = new File(receiveListener.getMp3Filename());
    try {
      final TextChannel channel = textChannel;

      double recordingSize = (double) dest.length() / 1024 / 1024;
      logger.info("Saving audio file '{}' from {} on {} of size {} MB.",
        dest.getName(), receiveListener.getVoiceChannel().getName(), guild.getName(), recordingSize);

      // TODO: This checks the size of the file and does something else if the file is bigger than what Discord allows, this doesn't work.
      if (dest.length() / 1024 / 1024 < 8) {
//            final TextChannel channel = textChannel;
        MessageBuilder message = new MessageBuilder();
        message.append("Unfortunately, current recordings are limited to the previous " + AudioReceiveListener.PCM_MINS + " minutes. Fixing this limit in upcoming releases.");
        channel.sendFile(dest, dest.getName(), message.build()).queue(null, (Throwable) -> {
          BotUtils.sendMessage(guild.getTextChannelById(defaultChannelId),
            "I don't have permissions to send files in " + channel.getName() + "!");
        });

        new Thread(() -> {
          try {
            sleep(1000 * 20); //20 second life for files sent to discord (no need to save)
          } catch (InterruptedException e) {
            logger.error("Failed during sleep", e);
          }

          boolean isDeleteSuccess = dest.delete();

          logger.info("Deleting file " + dest.getName() + "...");

          if (isDeleteSuccess)
            logger.info("Successfully deleted file {}. ", dest.getName());
          else
            logger.error("Could not delete file {}.", dest.getName());

        }).start();

      } else {
        BotUtils.sendMessage(channel, "Could not upload to Discord, file too large: " + recordingSize + "MB.");
            /*BotUtils.sendMessage(textChannel, "http://DiscordEcho.com/" + dest.getName());

            new Thread(() -> {
              try {
                sleep(1000 * 60 * 60);
              } catch (Exception ex) {
              }    //1 hour life for files stored on web server

              dest.delete();
              System.out.println("\tDeleting file " + dest.getName() + "...");

            }).start();*/
      }


    } catch (Exception e) {
      logger.error("Unknown error sending file", e);
      BotUtils.sendMessage(textChannel, "Unknown error sending file");
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

    File[] fileList = dir.listFiles();

    if (fileList != null) {
      for (File f : fileList) {
        if (f.getName().equals(saltStr)) {
          saltStr = getPJSaltString();
        }
      }
    }

    return saltStr;
  }

  //encode the passed array of PCM (uncompressed) audio to mp3 audio data
  @Deprecated
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
  public static AudioReceiveHandler killAudioHandlers(Guild g) {
    CombinedAudioRecorderHandler ah = (CombinedAudioRecorderHandler) g.getAudioManager().getReceiveHandler();
//    AudioReceiveListener ah = (AudioReceiveListener) g.getAudioManager().getReceiveHandler();

    if (ah != null) {
      ah.disconnect();
      g.getAudioManager().setReceivingHandler(null);
    }

    AudioSendListener sh = (AudioSendListener) g.getAudioManager().getSendingHandler();
    if (sh != null) {
      sh.canProvide = false;
      sh.voiceData = null;
      g.getAudioManager().setSendingHandler(null);
    }

    System.out.println("Destroyed audio handlers for " + g.getName());

    return ah;
  }
}
