package tech.gdragon;


import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;

public final class App extends NanoHTTPD {

  private App(int port) {
    super(port);
  }

  @Override
  public Response serve(IHTTPSession session) {
    String botUrl = "https://discordapp.com/oauth2/authorize?client_id=333132997245272067&scope=bot&permissions=36703232";
    Response response = newFixedLengthResponse(Response.Status.REDIRECT, NanoHTTPD.MIME_HTML, "");
    response.addHeader("Location", botUrl);
    return response;
  }

  /**
   * Starts a simple HTTP Service, whose only response is to redirect to the bot's page.
   */
  public static void main(String[] args) {
    App app = new App(8080);
    try {
      app.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
