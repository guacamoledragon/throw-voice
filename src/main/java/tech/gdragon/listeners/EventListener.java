package tech.gdragon.listeners;

import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.core.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.managers.AudioManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.gdragon.BotUtils;
import tech.gdragon.commands.CommandHandler;
import tech.gdragon.db.Shim;
import tech.gdragon.db.dao.Channel;
import tech.gdragon.db.dao.Settings;
import tech.gdragon.db.dao.User;
import tech.gdragon.listener.CombinedAudioRecorderHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


public class EventListener extends ListenerAdapter {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  @Override
  public void onGuildJoin(GuildJoinEvent event) {
    Shim.INSTANCE.xaction(() -> {
      long id = event.getGuild().getIdLong();
      String name = event.getGuild().getName();

      tech.gdragon.db.dao.Guild.findOrCreate(id, name);

      logger.info("Joined new server '{}', connected to {} guilds\n", event.getGuild().getName(), event.getJDA().getGuilds().size());

      return null;
    });
  }

  @Override
  public void onGuildLeave(GuildLeaveEvent event) {
    Shim.INSTANCE.xaction(() -> {
      long id = event.getGuild().getIdLong();

      tech.gdragon.db.dao.Guild guild = tech.gdragon.db.dao.Guild.Companion.findById(id);

      if (guild != null)
        guild.delete();

      logger.info("Left server '{}', connected to {} guilds\n", event.getGuild().getName(), event.getJDA().getGuilds().size());
      return null;
    });
  }

  @Override
  public void onGuildVoiceJoin(GuildVoiceJoinEvent event) {
    if (event.getMember() == null || event.getMember().getUser() == null || event.getMember().getUser().isBot())
      return;

    AudioManager audioManager = event.getGuild().getAudioManager();

    if (audioManager.isConnected()) {
      int newSize = BotUtils.voiceChannelSize(event.getChannelJoined());
      int botSize = BotUtils.voiceChannelSize(audioManager.getConnectedChannel());
      int min = Shim.INSTANCE.xaction(() -> {
        Settings settings = tech.gdragon.db.dao.Guild.Companion.findById(event.getGuild().getIdLong()).getSettings();

        for (Channel channel : settings.getChannels()) {
          if (channel.getId().getValue() == event.getChannelJoined().getIdLong()) {
            Integer autoJoin = channel.getAutoJoin();
            return autoJoin == null ? Integer.MAX_VALUE : autoJoin;
          }
        }

        return Integer.MAX_VALUE;
      });

      if (newSize >= min && botSize < newSize) {  //check for tie with old server
        boolean autoSave = Shim.INSTANCE.xaction(() -> {
          Settings settings = tech.gdragon.db.dao.Guild.Companion.findById(event.getGuild().getIdLong()).getSettings();
          return settings.getAutoSave();
        });

        if (autoSave) {
          CombinedAudioRecorderHandler receiveHandler = (CombinedAudioRecorderHandler) audioManager.getReceiveHandler();
          receiveHandler.saveRecording(event.getChannelJoined(), event.getMember().getDefaultChannel());
        }

        BotUtils.joinVoiceChannel(event.getChannelJoined(), false);
      }

    } else {
      VoiceChannel biggestChannel = BotUtils.biggestChannel(event.getGuild());

      if (biggestChannel != null) {
        BotUtils.joinVoiceChannel(event.getChannelJoined(), false);
      }
    }
  }

  @Override
  public void onGuildVoiceLeave(GuildVoiceLeaveEvent event) {
    if (event.getMember() == null || event.getMember().getUser() == null || event.getMember().getUser().isBot())
      return;

    int min = Shim.INSTANCE.xaction(() -> {
      Settings settings = tech.gdragon.db.dao.Guild.Companion.findById(event.getGuild().getIdLong()).getSettings();

      for (Channel channel : settings.getChannels()) {
        if (channel.getId().getValue() == event.getChannelLeft().getIdLong()) {
          return channel.getAutoLeave();
        }
      }

      return Integer.MAX_VALUE;
    });

    int size = BotUtils.voiceChannelSize(event.getChannelLeft());

    AudioManager audioManager = event.getGuild().getAudioManager();

    if (size <= min && audioManager.getConnectedChannel() == event.getChannelLeft()) {
      boolean autoSave = Shim.INSTANCE.xaction(() -> {
        Settings settings = tech.gdragon.db.dao.Guild.Companion.findById(event.getGuild().getIdLong()).getSettings();
        return settings.getAutoSave();
      });

      if (autoSave) {
        CombinedAudioRecorderHandler receiveHandler = (CombinedAudioRecorderHandler) audioManager.getReceiveHandler();
        receiveHandler.saveRecording(event.getChannelLeft(), event.getMember().getDefaultChannel());
      }

      BotUtils.leaveVoiceChannel(audioManager.getConnectedChannel());

      VoiceChannel biggest = BotUtils.biggestChannel(event.getGuild());
      if (biggest != null) {
        BotUtils.joinVoiceChannel(biggest, false);
      }
    }
  }

  @Override
  public void onGuildVoiceMove(GuildVoiceMoveEvent event) {
    if (event.getMember() == null || event.getMember().getUser() == null || event.getMember().getUser().isBot())
      return;

    AudioManager audioManager = event.getGuild().getAudioManager();

    if (audioManager.isConnected()) {
      int newSize = BotUtils.voiceChannelSize(event.getChannelJoined());
      int botSize = BotUtils.voiceChannelSize(audioManager.getConnectedChannel());

      int min = Shim.INSTANCE.xaction(() -> {
        Settings settings = tech.gdragon.db.dao.Guild.Companion.findById(event.getGuild().getIdLong()).getSettings();

        for (Channel channel : settings.getChannels()) {
          if (channel.getId().getValue() == event.getChannelJoined().getIdLong()) {
            Integer autoJoin = channel.getAutoJoin();
            return autoJoin == null ? Integer.MAX_VALUE : autoJoin;
          }
        }

        return Integer.MAX_VALUE;
      });

      if (newSize >= min && botSize < newSize) {  //check for tie with old server
        boolean autoSave = Shim.INSTANCE.xaction(() -> {
          Settings settings = tech.gdragon.db.dao.Guild.Companion.findById(event.getGuild().getIdLong()).getSettings();
          return settings.getAutoSave();
        });

        if (autoSave) {
          CombinedAudioRecorderHandler receiveHandler = (CombinedAudioRecorderHandler) audioManager.getReceiveHandler();
          receiveHandler.saveRecording(event.getChannelLeft(), event.getMember().getDefaultChannel());
        }

        BotUtils.joinVoiceChannel(event.getChannelJoined(), false);
      }

    } else {
      VoiceChannel biggestChannel = BotUtils.biggestChannel(event.getGuild());
      if (biggestChannel != null) {
        BotUtils.joinVoiceChannel(biggestChannel, false);
      }
    }

    //Check if bot needs to leave old channel
    int min = Shim.INSTANCE.xaction(() -> {
      Settings settings = tech.gdragon.db.dao.Guild.Companion.findById(event.getGuild().getIdLong()).getSettings();

      for (Channel channel : settings.getChannels()) {
        if (channel.getId().getValue() == event.getChannelJoined().getIdLong()) {
          return channel.getAutoLeave();
        }
      }

      return 0;
    });
    int size = BotUtils.voiceChannelSize(event.getChannelLeft());

    if (audioManager.isConnected() && size <= min && audioManager.getConnectedChannel() == event.getChannelLeft()) {
      boolean autoSave = Shim.INSTANCE.xaction(() -> {
        Settings settings = tech.gdragon.db.dao.Guild.Companion.findById(event.getGuild().getIdLong()).getSettings();
        return settings.getAutoSave();
      });

      if (autoSave) {
        CombinedAudioRecorderHandler receiveHandler = (CombinedAudioRecorderHandler) audioManager.getReceiveHandler();
        receiveHandler.saveRecording(event.getChannelLeft(), event.getMember().getDefaultChannel());
      }

      BotUtils.leaveVoiceChannel(audioManager.getConnectedChannel());

      VoiceChannel biggest = BotUtils.biggestChannel(event.getGuild());
      if (biggest != null) {
        BotUtils.joinVoiceChannel(event.getChannelJoined(), false);
      }
    }
  }

  @Override
  public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
    if (event.getMember() == null || event.getMember().getUser() == null || event.getMember().getUser().isBot())
      return;

    long guildId = event.getGuild().getIdLong();

    String prefix = Shim.INSTANCE.xaction(() -> {
      tech.gdragon.db.dao.Guild guild = tech.gdragon.db.dao.Guild.Companion.findById(guildId);

      // HACK: Create settings for a guild that needs to be accessed. This is a problem when restarting bot.
      // TODO: On bot initialization, I should be able to check which Guilds the bot is connected to and purge/add respectively
      if (guild == null) {
        guild = tech.gdragon.db.dao.Guild.findOrCreate(guildId, event.getGuild().getName());
      }

      return guild.getSettings().getPrefix();
    });

    String rawContent = event.getMessage().getContentDisplay();
    if (rawContent.startsWith(prefix)) {
      // TODO: handle any CommandHandler exceptions here
      CommandHandler.handleCommand(event, CommandHandler.parser.parse(prefix, rawContent.toLowerCase()));
    } else if (rawContent.equals("!help")) { // force help to always work with "!" prefix
      CommandHandler.handleCommand(event, CommandHandler.parser.parse(prefix, prefix + "help"));
    }
  }

  @Override
  public void onPrivateMessageReceived(PrivateMessageReceivedEvent event) {
    if (event.getAuthor() == null || event.getAuthor().isBot())
      return;

    String message = event.getMessage().getContentDisplay();

    if (message.startsWith("!alerts")) {
      if (message.endsWith("off")) {
        for (Guild g : event.getJDA().getGuilds()) {
          if (g.getMember(event.getAuthor()) != null) {
            Shim.INSTANCE.xaction(() -> {
              Settings settings = tech.gdragon.db.dao.Guild.Companion.findById(g.getIdLong()).getSettings();
              return User.findOrCreate(event.getAuthor().getIdLong(), event.getAuthor().getName(), settings);
            });
          }
        }
        event.getChannel().sendMessage("Alerts now off, message `!alerts on` to re-enable at any time").queue();

      } else if (message.endsWith("on")) {
        for (Guild g : event.getJDA().getGuilds()) {
          if (g.getMember(event.getAuthor()) != null) {
            Shim.INSTANCE.xaction(() -> {
              Settings settings = tech.gdragon.db.dao.Guild.Companion.findById(g.getIdLong()).getSettings();
              User user = User.findOrCreate(event.getAuthor().getIdLong(), event.getAuthor().getName(), settings);
              user.delete();
              return user;
            });
          }
        }
        event.getChannel().sendMessage("Alerts now on, message `!alerts off` to disable at any time").queue();
      } else {
        event.getChannel().sendMessage("!alerts [on | off]").queue();
      }

        /* removed because prefix and aliases are dependent on guild, which cannot be assumed without a message sent from guild
        } else if (message.startsWith("!help")) {

            EmbedBuilder embed = new EmbedBuilder();
            embed.setAuthor("Discord Echo", "http://DiscordEcho.com/", event.getJDA().getSelfUser().getAvatarUrl());
            embed.setColor(Color.RED);
            embed.setTitle("Currently in beta, being actively developed and tested. Expect bugs.");
            embed.setDescription("Join my guild for updates - https://discord.gg/JWNFSZJ");
            embed.setThumbnail("http://www.freeiconspng.com/uploads/information-icon-5.png");
            embed.setFooter("Replace brackets [] with item specified. Vertical bar | means 'or', either side of bar is valid choice.", "http://www.niceme.me");
            embed.addBlankField(false);

            Object[] cmds = CommandHandler.commands.keySet().toArray();
            Arrays.sort(cmds);
            for (Object command : cmds) {
                if (command == "help") continue;
                embed.addField(CommandHandler.commands.get(command).usage("!"), CommandHandler.commands.get(command).description(), true);
            }

            event.getChannel().sendMessage(embed.build()).queue();
        */
    } else {
      event.getChannel().sendMessage("DM commands unsupported, send `!help` in your guild chat for more info.").queue();
    }
  }

  @Override
  public void onReady(ReadyEvent event) {
    event
      .getJDA()
      .getPresence()
      .setGame(new Game("!help | http://pawabot.site", "http://pawabot.site", Game.GameType.DEFAULT) {
      });

    logger.info("ONLINE: Connected to {} guilds!", event.getJDA().getGuilds().size());

    // Add guild if not present
    for (Guild g : event.getJDA().getGuilds()) {
      tech.gdragon.db.dao.Guild.findOrCreate(g.getIdLong(), g.getName());
    }

    try {
      Path dir = Paths.get("/var/www/html/");
      if (Files.notExists(dir)) {
        String dataDirectory = System.getenv("DATA_DIR");
        dir = Files.createDirectories(Paths.get(dataDirectory + "/recordings/"));
        logger.info("Creating: " + dir.toString());
      }

      Files
        .list(dir)
        .filter(path -> Files.isRegularFile(path) && path.toString().toLowerCase().endsWith(".mp3"))
        .forEach(path -> {
          try {
            Files.delete(path);
            logger.info("Deleting file " + path + "...");
          } catch (IOException e1) {
            logger.error("Could not delete: " + path, e1);
          }
        });
    } catch (IOException e1) {
      logger.error("Error preparing to read recordings", e1);
    }

    //check for servers to join
    for (Guild g : event.getJDA().getGuilds()) {
      VoiceChannel biggest = BotUtils.biggestChannel(g);
      if (biggest != null) {
        BotUtils.joinVoiceChannel(BotUtils.biggestChannel(g), false);
      }
    }
  }
}
