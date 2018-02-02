package tech.gdragon;


import fi.iki.elonen.NanoHTTPD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.gdragon.db.Shim;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class App extends NanoHTTPD {

  private static final Logger logger = LoggerFactory.getLogger(App.class);

  private String clientId = "";

  private App(int port, String clientId) {
    super(port);
    this.clientId = clientId;
  }

  @Override
  public Response serve(IHTTPSession session) {
    String uri = session.getUri();

    logger.debug(uri);

    Response response;
    if (uri.toLowerCase().contains("ping")) {
      response = newFixedLengthResponse("pong");
    } else {
      // TODO We don't gotta hand jam this, api.asBot().getInviteUrl(...)
      String botUrl = "https://discordapp.com/oauth2/authorize?client_id=" + this.clientId + "&scope=bot&permissions=" + DiscordBot.PERMISSIONS;
      response = newFixedLengthResponse(Response.Status.REDIRECT, NanoHTTPD.MIME_HTML, "");
      response.addHeader("Location", botUrl);
    }

    return response;
  }

  /**
   * Starts a simple HTTP Service, whose only response is to redirect to the bot's page.
   */
  public static void main(String[] args) {
    String token = System.getenv("BOT_TOKEN");
    String port = System.getenv("PORT");
    String clientId = System.getenv("CLIENT_ID");
    String dataDirectory = System.getenv("DATA_DIR");

    // Connect to database
    Shim.INSTANCE.initializeDatabase(dataDirectory + "/settings.db");

    App app = new App(Integer.parseInt(port), clientId);

    // HACK: Create directory here cause for some reason it doesn't get created otherwise
    try {
      Files.createDirectories(Paths.get(dataDirectory + "/recordings/"));
    } catch (IOException e) {
      logger.error("Could not create recordings directory", e);
    }

    new DiscordBot(token);

    try {
      logger.info("Starting HTTP Server: http://localhost:" + port);
      app.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
