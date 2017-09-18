package tech.gdragon.commands.settings;

import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import org.jetbrains.exposed.sql.SizedIterable;
import tech.gdragon.DiscordBot;
import tech.gdragon.commands.Command;
import tech.gdragon.db.Shim;
import tech.gdragon.db.dao.Guild;
import tech.gdragon.db.dao.User;
import tech.gdragon.db.table.Tables;


public class AlertsCommand implements Command {
  @Override
  public void action(String[] args, GuildMessageReceivedEvent e) {
    long guildId = e.getGuild().getIdLong();
    String prefix = Shim.INSTANCE.xaction(() -> Guild.Companion.findById(guildId).getSettings().getPrefix());
    TextChannel channel = e.getChannel();
    String message = usage(prefix);

    if (args.length != 1) {
      DiscordBot.sendMessage(channel, message);
      return;
    }

    net.dv8tion.jda.core.entities.User author = e.getAuthor();

    switch (args[0]) {
      case "off":
        Shim.INSTANCE.xaction(() -> {
          Guild guild = Guild.Companion.findById(guildId);
          return User.Companion.create(author.getIdLong(), author.getName(), guild.getSettings());
        });

        message = "Alerts now off, message `" + prefix + "alerts on` to re-enable at any time";
        break;
      case "on":
        Shim.INSTANCE.xaction(() -> {
          SizedIterable<User> user = User.Companion.find(sql -> sql.eq(Tables.Users.INSTANCE.getDiscordId(), author.getIdLong()));
          user.forEach(User::delete);

          return null;
        });

        message = "Alerts now on, message `" + prefix + "alerts off` to disable at any time";
        break;
    }

    DiscordBot.sendMessage(channel, message);
  }

  @Override
  public String usage(String prefix) {
    return prefix + "alerts [on | off]";
  }

  @Override
  public String description() {
    return "Turns on/off direct message alerts for when you are being recorded in a voice channel "
      + "(on by default)";
  }
}
