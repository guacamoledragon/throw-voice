package tech.gdragon;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import tech.gdragon.commands.CommandHandler;
import tech.gdragon.commands.audio.Clip;
import tech.gdragon.commands.audio.EchoCommand;
import tech.gdragon.commands.audio.MessageInABottleCommand;
import tech.gdragon.commands.audio.Save;
import tech.gdragon.commands.misc.Help;
import tech.gdragon.commands.misc.Join;
import tech.gdragon.commands.misc.Leave;
import tech.gdragon.commands.settings.*;
import tech.gdragon.listeners.EventListener;

import javax.security.auth.login.LoginException;

public class DiscordBot {
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
      CommandHandler.commands.put("clip", new Clip());
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
}
