package tech.gdragon;


import fi.iki.elonen.NanoHTTPD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public final class App extends NanoHTTPD {

  private static final Logger logger = LoggerFactory.getLogger(App.class);

  private String clientId = "";

  private App(int port, String clientId) {
    super(port);
    this.clientId = clientId;
  }

  @Override
  public Response serve(IHTTPSession session) {
    // 36703232
    String botUrl = "https://discordapp.com/oauth2/authorize?client_id=" + this.clientId + "&scope=bot&permissions=" + DiscordBot.PERMISSIONS;
    Response response = newFixedLengthResponse(Response.Status.REDIRECT, NanoHTTPD.MIME_HTML, "");
    response.addHeader("Location", botUrl);
    return response;
  }

  /**
   * Starts a simple HTTP Service, whose only response is to redirect to the bot's page.
   */
  public static void main(String[] args) {
    String token = System.getenv("BOT_TOKEN");
    String port = System.getenv("PORT");
    String clientId = System.getenv("CLIENT_ID");

    App app = new App(Integer.parseInt(port), clientId);
    new DiscordBot(token);

    try {
      logger.info("Starting HTTP Server: http://localhost:" + port);
      app.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
