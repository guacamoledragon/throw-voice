package tech.gdragon.listeners;

import net.dv8tion.jda.core.audio.AudioSendHandler;

import java.util.Arrays;


public class AudioSendListener implements AudioSendHandler {
  public byte[][] voiceData;
  public boolean canProvide;
  int index;

  public AudioSendListener(byte[] data) {
    canProvide = true;
    voiceData = new byte[data.length / 3840][3840];
    for (int i = 0; i < voiceData.length; i++) {
      voiceData[i] = Arrays.copyOfRange(data, i * 3840, i * 3840 + 3840);
    }
  }

  @Override
  public boolean canProvide() {
    return canProvide;
  }

  @Override
  public byte[] provide20MsAudio() {
    if (index == voiceData.length - 1) {
      canProvide = false;
    }
    return voiceData[index++];
  }

  @Override
  public boolean isOpus() {
    return false;
  }
}
