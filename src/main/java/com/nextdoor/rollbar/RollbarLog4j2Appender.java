/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * Copyright 2017 Nextdoor.com, Inc
 *
 */

package com.nextdoor.rollbar;

import com.rollbar.api.payload.data.Level;
import com.rollbar.api.payload.data.Server;
import com.rollbar.notifier.Rollbar;
import com.rollbar.notifier.config.ConfigBuilder;
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
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static com.rollbar.notifier.config.ConfigBuilder.withAccessToken;

@Plugin(name = "Rollbar", category = "Core", elementType = "appender", printObject = true)
public class RollbarLog4j2Appender extends AbstractAppender {
  private final Rollbar client;

  protected RollbarLog4j2Appender(String name, Filter filter, Layout<? extends Serializable> layout,
                                  boolean ignoreExceptions, Rollbar client) {
    super(name, filter, layout, ignoreExceptions);
    this.client = client;
  }

  @PluginFactory
  public static Appender createAppender(@PluginAttribute("name") String name,
                                        @PluginElement("Layout") Layout<? extends Serializable> layout,
                                        @PluginElement("Filter") final Filter filter,
                                        @PluginAttribute("accessToken") String accessToken,
                                        @PluginAttribute("environment") String environment) {
    boolean isNoopLogger = false;
    Appender appender;

    if (name == null) {
      LOGGER.error("No name provided for RollbarLog4j2Appender");
      return null;
    }

    appender = NullAppender.createAppender(name);

    if (accessToken == null || accessToken.isEmpty()) {
      LOGGER.error("'accessToken' must be set for RollbarLog4j2Appender");
      isNoopLogger = true;
    }

    if (environment == null || environment.isEmpty()) {
      LOGGER.warn("Defaulting 'environment' to 'production'");
      environment = "production";
    }

    if (layout == null) {
      layout = PatternLayout.createDefaultLayout();
    }

    if (!isNoopLogger) {
      ConfigBuilder config = withAccessToken(accessToken)
        .environment(environment);

      try {
        String hostName = InetAddress.getLocalHost().getHostName();
        config.server(new Server.Builder().host(hostName)::build);
      } catch (UnknownHostException | IllegalArgumentException e) {
        LOGGER.error("unable to get hostname", e);
      }

      final Rollbar rollbar = Rollbar.init(config.build());
      appender = new RollbarLog4j2Appender(name, filter, layout, false, rollbar);
    }

    return appender;
  }

  public void append(LogEvent event) {
    Level rollbarLevel;

    if (event.getLevel() == org.apache.logging.log4j.Level.INFO) {
      rollbarLevel = Level.INFO;
    } else if (event.getLevel() == org.apache.logging.log4j.Level.TRACE
      || event.getLevel() == org.apache.logging.log4j.Level.DEBUG) {
      rollbarLevel = Level.DEBUG;
    } else if (event.getLevel() == org.apache.logging.log4j.Level.WARN) {
      rollbarLevel = Level.WARNING;
    } else if (event.getLevel() == org.apache.logging.log4j.Level.ERROR) {
      rollbarLevel = Level.ERROR;
    } else if (event.getLevel() == org.apache.logging.log4j.Level.FATAL) {
      rollbarLevel = Level.CRITICAL;
    } else {
      return;
    }

    final String formattedMessage = new String(getLayout().toByteArray(event));
    if (event.getThrown() != null) {
      if (event.getMessage().toString() != null) {
        this.client.log(event.getThrown(), formattedMessage, rollbarLevel);
      } else {
        this.client.log(event.getThrown(), rollbarLevel);
      }
    } else {
      this.client.log(formattedMessage, rollbarLevel);
    }
  }
}
