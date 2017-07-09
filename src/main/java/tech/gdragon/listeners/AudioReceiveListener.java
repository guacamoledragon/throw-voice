package tech.gdragon.listeners;

import net.dv8tion.jda.core.audio.AudioReceiveHandler;
import net.dv8tion.jda.core.audio.CombinedAudio;
import net.dv8tion.jda.core.audio.UserAudio;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.VoiceChannel;
import tech.gdragon.DiscordBot;

import java.util.Arrays;

import static tech.gdragon.DiscordBot.serverSettings;

public class AudioReceiveListener implements AudioReceiveHandler {
  public static final double STARTING_MB = 0.5;
  public static final int CAP_MB = 16;
  public static final double PCM_MINS = 2;
  private final double AFK_LIMIT = 2;
  public boolean canReceive = true;
  public double volume = 1.0;
  private VoiceChannel voiceChannel;

  public byte[] uncompVoiceData = new byte[(int) (3840 * 50 * 60 * PCM_MINS)]; //3840bytes/array * 50arrays/sec * 60sec = 1 mins
  public int uncompIndex = 0;

  public byte[] compVoiceData = new byte[(int) (1024 * 1024 * STARTING_MB)];    //start with 0.5 MB
  public int compIndex = 0;

  public boolean overwriting = false;

  private int afkTimer;

  public AudioReceiveListener(double volume, VoiceChannel voiceChannel) {
    this.volume = volume;
    this.voiceChannel = voiceChannel;
  }

  @Override
  public boolean canReceiveCombined() {
    return canReceive;
  }

  @Override
  public boolean canReceiveUser() {
    return false;
  }

  @Override
  public void handleCombinedAudio(CombinedAudio combinedAudio) {
    if (combinedAudio.getUsers().size() == 0) {
      afkTimer++;
    } else {
      afkTimer = 0;
    }

    if (afkTimer >= 50 * 60 * AFK_LIMIT) {   //20ms * 50 * 60 seconds * 2 mins = 2 mins
      System.out.format("AFK detected, leaving '%s' voice channel in %s\n", voiceChannel.getName(), voiceChannel.getGuild().getName());
      TextChannel defaultTC = voiceChannel.getGuild().getTextChannelById(serverSettings.get(voiceChannel.getGuild().getId()).defaultTextChannel);
      DiscordBot.sendMessage(defaultTC, "No audio for 2 minutes, leaving from AFK detection...");

      voiceChannel.getGuild().getAudioManager().closeAudioConnection();
      DiscordBot.killAudioHandlers(voiceChannel.getGuild());
      return;
    }

    if (uncompIndex == uncompVoiceData.length / 2 || uncompIndex == uncompVoiceData.length) {
      new Thread(() -> {

        if (uncompIndex < uncompVoiceData.length / 2)  //first half
          addCompVoiceData(DiscordBot.encodePcmToMp3(Arrays.copyOfRange(uncompVoiceData, 0, uncompVoiceData.length / 2)));
        else
          addCompVoiceData(DiscordBot.encodePcmToMp3(Arrays.copyOfRange(uncompVoiceData, uncompVoiceData.length / 2, uncompVoiceData.length)));

      }).start();

      if (uncompIndex == uncompVoiceData.length)
        uncompIndex = 0;
    }

    for (byte b : combinedAudio.getAudioData(volume)) {
      uncompVoiceData[uncompIndex++] = b;
    }
  }

  public byte[] getVoiceData() {
    canReceive = false;

    //flush remaining audio
    byte[] remaining = new byte[uncompIndex];

    int start = uncompIndex < uncompVoiceData.length / 2 ? 0 : uncompVoiceData.length / 2;

    for (int i = 0; i < uncompIndex - start; i++) {
      remaining[i] = uncompVoiceData[start + i];
    }

    addCompVoiceData(DiscordBot.encodePcmToMp3(remaining));

    byte[] orderedVoiceData;
    if (overwriting) {
      orderedVoiceData = new byte[compVoiceData.length];
    } else {
      orderedVoiceData = new byte[compIndex + 1];
      compIndex = 0;
    }

    for (int i = 0; i < orderedVoiceData.length; i++) {
      if (compIndex + i < orderedVoiceData.length)
        orderedVoiceData[i] = compVoiceData[compIndex + i];
      else
        orderedVoiceData[i] = compVoiceData[compIndex + i - orderedVoiceData.length];
    }

    wipeMemory();
    canReceive = true;

    return orderedVoiceData;
  }


  public void addCompVoiceData(byte[] compressed) {
    for (byte b : compressed) {
      if (compIndex >= compVoiceData.length && compVoiceData.length != 1024 * 1024 * CAP_MB) {    //cap at 16MB

        byte[] temp = new byte[compVoiceData.length * 2];
        System.arraycopy(compVoiceData, 0, temp, 0, compVoiceData.length);

        compVoiceData = temp;

      } else if (compIndex >= compVoiceData.length && compVoiceData.length == 1024 * 1024 * CAP_MB) {
        compIndex = 0;

        if (!overwriting) {
          overwriting = true;
          System.out.format("Hit compressed storage cap in %s on %s", voiceChannel.getName(), voiceChannel.getGuild().getName());
        }
      }


      compVoiceData[compIndex++] = b;
    }
  }


  public void wipeMemory() {
    System.out.format("Wiped recording data in %s on %s", voiceChannel.getName(), voiceChannel.getGuild().getName());
    uncompIndex = 0;
    compIndex = 0;

    compVoiceData = new byte[1024 * 1024 / 2];
    System.gc();
  }


  public byte[] getUncompVoice(int time) {
    canReceive = false;

    if (time > PCM_MINS * 60 * 2) {     //2 mins
      time = (int) (PCM_MINS * 60 * 2);
    }
    int requestSize = 3840 * 50 * time;
    byte[] voiceData = new byte[requestSize];

    for (int i = 0; i < voiceData.length; i++) {
      if (uncompIndex + i < voiceData.length) {
        voiceData[i] = uncompVoiceData[uncompIndex + i];
      } else {
        voiceData[i] = uncompVoiceData[uncompIndex + i - voiceData.length];
      }
    }

    wipeMemory();
    canReceive = true;
    return voiceData;
  }

  @Override
  public void handleUserAudio(UserAudio userAudio) {
  }
}
