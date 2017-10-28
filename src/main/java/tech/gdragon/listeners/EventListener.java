package tech.gdragon.listeners;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.gdragon.BotUtils;
import tech.gdragon.DiscordBot;
import tech.gdragon.commands.CommandHandler;
import tech.gdragon.configuration.ServerSettings;
import tech.gdragon.db.Shim;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;


public class EventListener extends ListenerAdapter {

  private final Logger logger = LoggerFactory.getLogger(this.getClass());

  @Override
  public void onGuildJoin(GuildJoinEvent e) {
    DiscordBot.serverSettings.put(e.getGuild().getId(), new ServerSettings(e.getGuild()));
    DiscordBot.writeSettingsJson();
    System.out.format("Joined new server '%s', connected to %s guilds\n", e.getGuild().getName(), e.getJDA().getGuilds().size());
  }

  @Override
  public void onGuildLeave(GuildLeaveEvent e) {
    DiscordBot.serverSettings.remove(e.getGuild().getId());
    DiscordBot.writeSettingsJson();
    System.out.format("Left server '%s', connected to %s guilds\n", e.getGuild().getName(), e.getJDA().getGuilds().size());
  }

  @Override
  public void onGuildVoiceJoin(GuildVoiceJoinEvent e) {
    if (e.getMember() == null || e.getMember().getUser() == null || e.getMember().getUser().isBot())
      return;

    VoiceChannel biggestChannel = BotUtils.biggestChannel(e.getGuild());

    if (e.getGuild().getAudioManager().isConnected()) {

      int newSize = BotUtils.voiceChannelSize(e.getChannelJoined());
      int botSize = BotUtils.voiceChannelSize(e.getGuild().getAudioManager().getConnectedChannel());
      ServerSettings settings = DiscordBot.serverSettings.get(e.getGuild().getId());
      int min = settings.autoJoinSettings.get(e.getChannelJoined().getId());

      if (newSize >= min && botSize < newSize) {  //check for tie with old server
        if (DiscordBot.serverSettings.get(e.getGuild().getId()).autoSave)
          DiscordBot.writeToFile(e.getGuild());  //write data from voice channel it is leaving

        DiscordBot.joinVoiceChannel(e.getChannelJoined(), false);
      }

    } else {
      if (biggestChannel != null) {
        DiscordBot.joinVoiceChannel(e.getChannelJoined(), false);
      }
    }
  }

  @Override
  public void onGuildVoiceLeave(GuildVoiceLeaveEvent e) {
    if (e.getMember() == null || e.getMember().getUser() == null || e.getMember().getUser().isBot())
      return;

    int min = DiscordBot.serverSettings.get(e.getGuild().getId()).autoLeaveSettings.get(e.getChannelLeft().getId());
    int size = BotUtils.voiceChannelSize(e.getChannelLeft());

    if (size <= min && e.getGuild().getAudioManager().getConnectedChannel() == e.getChannelLeft()) {

      if (DiscordBot.serverSettings.get(e.getGuild().getId()).autoSave)
        DiscordBot.writeToFile(e.getGuild());  //write data from voice channel it is leaving

      DiscordBot.leaveVoiceChannel(e.getGuild().getAudioManager().getConnectedChannel());

      VoiceChannel biggest = BotUtils.biggestChannel(e.getGuild());
      if (biggest != null) {
        DiscordBot.joinVoiceChannel(biggest, false);
      }
    }
  }

  @Override
  public void onGuildVoiceMove(GuildVoiceMoveEvent e) {
    if (e.getMember() == null || e.getMember().getUser() == null || e.getMember().getUser().isBot())
      return;

    //Check if bot needs to join newly joined channel
    VoiceChannel biggestChannel = BotUtils.biggestChannel(e.getGuild());

    if (e.getGuild().getAudioManager().isConnected()) {

      int newSize = BotUtils.voiceChannelSize(e.getChannelJoined());
      int botSize = BotUtils.voiceChannelSize(e.getGuild().getAudioManager().getConnectedChannel());
      ServerSettings settings = DiscordBot.serverSettings.get(e.getGuild().getId());
      int min = settings.autoJoinSettings.get(e.getChannelJoined().getId());

      if (newSize >= min && botSize < newSize) {  //check for tie with old server
        if (DiscordBot.serverSettings.get(e.getGuild().getId()).autoSave)
          DiscordBot.writeToFile(e.getGuild());  //write data from voice channel it is leaving

        DiscordBot.joinVoiceChannel(e.getChannelJoined(), false);
      }

    } else {
      if (biggestChannel != null) {
        DiscordBot.joinVoiceChannel(biggestChannel, false);
      }
    }

    //Check if bot needs to leave old channel
    int min = DiscordBot.serverSettings.get(e.getGuild().getId()).autoLeaveSettings.get(e.getChannelLeft().getId());
    int size = BotUtils.voiceChannelSize(e.getChannelLeft());

    if (size <= min && e.getGuild().getAudioManager().getConnectedChannel() == e.getChannelLeft()) {

      if (DiscordBot.serverSettings.get(e.getGuild().getId()).autoSave)
        DiscordBot.writeToFile(e.getGuild());  //write data from voice channel it is leaving

      DiscordBot.leaveVoiceChannel(e.getGuild().getAudioManager().getConnectedChannel());

      VoiceChannel biggest = BotUtils.biggestChannel(e.getGuild());
      if (biggest != null) {
        DiscordBot.joinVoiceChannel(e.getChannelJoined(), false);
      }
    }
  }

  @Override
  public void onGuildMessageReceived(GuildMessageReceivedEvent e) {
    if (e.getMember() == null || e.getMember().getUser() == null || e.getMember().getUser().isBot())
      return;

    long guildId = e.getGuild().getIdLong();

//    String prefix = DiscordBot.serverSettings.get(guildId).prefix;
    String prefix = Shim.INSTANCE.xaction(() -> {
      tech.gdragon.db.dao.Guild guild = tech.gdragon.db.dao.Guild.Companion.findById(guildId);

      // HACK: Create settings for a guild that needs to be accessed. This is a problem when restarting bot.
      // TODO: On bot initialization, I should be able to check which Guilds the bot is connected to and purge/add respectively
      if (guild == null) {
        guild = Shim.INSTANCE.createGuild(guildId, e.getGuild().getName());
      }

      return guild.getSettings().getPrefix();
    });

    //force help to always work with "!" prefix
    if (e.getMessage().getContent().startsWith(prefix) || e.getMessage().getContent().startsWith("!help")) {
      CommandHandler.handleCommand(e, CommandHandler.parser.parse(e.getMessage().getContent().toLowerCase(), e));
    }
  }

  @Override
  public void onPrivateMessageReceived(PrivateMessageReceivedEvent e) {
    if (e.getAuthor() == null || e.getAuthor().isBot())
      return;

    if (e.getMessage().getContent().startsWith("!alerts")) {
      if (e.getMessage().getContent().endsWith("off")) {
        for (Guild g : e.getJDA().getGuilds()) {
          if (g.getMember(e.getAuthor()) != null) {
            DiscordBot.serverSettings.get(g.getId()).alertBlackList.add(e.getAuthor().getId());
          }
        }
        e.getChannel().sendMessage("Alerts now off, message `!alerts on` to re-enable at any time").queue();
        DiscordBot.writeSettingsJson();

      } else if (e.getMessage().getContent().endsWith("on")) {
        for (Guild g : e.getJDA().getGuilds()) {
          if (g.getMember(e.getAuthor()) != null) {
            DiscordBot.serverSettings.get(g.getId()).alertBlackList.remove(e.getAuthor().getId());
          }
        }
        e.getChannel().sendMessage("Alerts now on, message `!alerts off` to disable at any time").queue();
        DiscordBot.writeSettingsJson();
      } else {
        e.getChannel().sendMessage("!alerts [on | off]").queue();
      }

        /* removed because prefix and aliases are dependent on guild, which cannot be assumed without a message sent from guild
        } else if (e.getMessage().getContent().startsWith("!help")) {

            EmbedBuilder embed = new EmbedBuilder();
            embed.setAuthor("Discord Echo", "http://DiscordEcho.com/", e.getJDA().getSelfUser().getAvatarUrl());
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

            e.getChannel().sendMessage(embed.build()).queue();
        */
    } else {
      e.getChannel().sendMessage("DM commands unsupported, send `!help` in your guild chat for more info.").queue();
    }
  }

  @Override
  public void onReady(ReadyEvent e) {
    e.getJDA().getPresence().setGame(new Game("!help | http://pawabot.site", "http://pawabot.site", Game.GameType.DEFAULT) {});

    try {
      System.out.format("ONLINE: Connected to %s guilds!\n", e.getJDA().getGuilds().size(), e.getJDA().getVoiceChannels().size());

      Gson gson = new Gson();

//      FileReader fileReader = new FileReader("settings.json");
//      BufferedReader buffered = new BufferedReader(fileReader);

      Type type = new TypeToken<HashMap<String, ServerSettings>>() {
      }.getType();

      DiscordBot.serverSettings = gson.fromJson("{}", type);

      if (DiscordBot.serverSettings == null)
        DiscordBot.serverSettings = new HashMap<>();

//      buffered.close();
//      fileReader.close();

    } catch (Exception ex) {
      ex.printStackTrace();
    }


    for (Guild g : e.getJDA().getGuilds()) {    //validate settings files
      if (!DiscordBot.serverSettings.containsKey(g.getId())) {
        DiscordBot.serverSettings.put(g.getId(), new ServerSettings(g));
        DiscordBot.writeSettingsJson();
      }
    }

    try {
      Path dir = Paths.get("/var/www/html/");
      if (Files.notExists(dir)) {
        dir = Files.createDirectories(Paths.get("recordings/"));
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
    for (Guild g : e.getJDA().getGuilds()) {
      VoiceChannel biggest = BotUtils.biggestChannel(g);
      if (biggest != null) {
        DiscordBot.joinVoiceChannel(BotUtils.biggestChannel(g), false);
      }
    }
  }
}
