package tech.gdragon.discord.logging;

import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.appender.NullAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.io.IOException;
import java.io.Serializable;

@Plugin(name = "Discord", category = "Core", elementType = "appender", printObject = true)
public class DiscordWebhookAppender extends AbstractAppender {
  private String webhookUrl;
  private OkHttpClient client;

  protected DiscordWebhookAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions, String webhookUrl) {
    super(name, filter, layout, ignoreExceptions);
    this.webhookUrl = webhookUrl;
    this.client = new OkHttpClient();
  }

  @PluginFactory
  public static Appender createAppender(@PluginAttribute("name") String name,
                                        @PluginElement("Layout") Layout<? extends Serializable> layout,
                                        @PluginElement("Filter") final Filter filter,
                                        @PluginAttribute("webhookUrl") String webhookUrl) {
    if (webhookUrl == null || webhookUrl.isEmpty()) {
      LOGGER.error("'webhookUrl' must be set for DiscordWebhookAppender");
      return NullAppender.createAppender(name);
    }

    return new DiscordWebhookAppender(name, filter, layout, false, webhookUrl);
  }

  @Override
  public void append(LogEvent event) {
    MultipartBody multipartBody = new MultipartBody.Builder()
      .setType(MultipartBody.FORM)
      .addFormDataPart("content", event.getMessage().getFormattedMessage())
      .build();

    Request request = new Request.Builder()
      .url(webhookUrl)
      .post(multipartBody)
      .build();

    Response response = null;
    try {
      response = client.newCall(request).execute();
      if(!response.isSuccessful()) {
        LOGGER.error("Could not send log message to Discord, error code: " + response.code());
      }
    } catch (IOException e) {
      LOGGER.error("Could not send log message to Discord", e);
    } finally {
      if(response != null && response.body() != null) {
        response.body().close();
      }
    }
  }
}
