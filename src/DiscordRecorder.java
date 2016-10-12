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

import javax.security.auth.login.LoginException;
import java.util.HashMap;
import java.util.Map;

public class DiscordRecorder
{
    public static boolean autoRecord = true;

    private Map<String,Player> players = new HashMap<>();

    public static void main(String[] args)
    {
        try
        {
            JDA api = new JDABuilder()
                    .setBotToken("REDACTED")
                    .addListener(new EventHandler())
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

        CommandHandler.commands.put("ping" , new PingCommand());
        CommandHandler.commands.put("auto" , new AutoRecordCommand());
    }
}
