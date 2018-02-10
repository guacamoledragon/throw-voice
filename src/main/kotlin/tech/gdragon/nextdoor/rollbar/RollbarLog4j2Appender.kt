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

package tech.gdragon.nextdoor.rollbar

import com.rollbar.api.payload.data.Server
import com.rollbar.notifier.Rollbar
import com.rollbar.notifier.config.ConfigBuilder.withAccessToken
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.Layout
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.appender.NullAppender
import org.apache.logging.log4j.core.config.plugins.Plugin
import org.apache.logging.log4j.core.config.plugins.PluginAttribute
import org.apache.logging.log4j.core.config.plugins.PluginElement
import org.apache.logging.log4j.core.config.plugins.PluginFactory
import org.apache.logging.log4j.core.layout.PatternLayout
import java.io.Serializable
import java.net.InetAddress
import java.net.UnknownHostException
import com.rollbar.api.payload.data.Level as RollbarLevel

@Plugin(name = "Rollbar", category = "Core", elementType = "appender", printObject = true)
class RollbarLog4j2Appender(name: String, filter: Filter, layout: Layout<out Serializable>,
                            ignoreExceptions: Boolean, private val client: Rollbar) : AbstractAppender(name, filter, layout, ignoreExceptions) {
  companion object {

    @PluginFactory
    fun createAppender(@PluginAttribute("name") name: String?,
                       @PluginElement("Layout") layout: Layout<out Serializable>?,
                       @PluginElement("Filter") filter: Filter,
                       @PluginAttribute("accessToken") accessToken: String?,
                       @PluginAttribute("environment") environment: String?): Appender? {
      var isNoopLogger = false
      var appender: Appender

      if (name == null) {
        LOGGER.error("No name provided for RollbarLog4j2Appender")
        return null
      }

      appender = NullAppender.createAppender(name)

      if (accessToken.isNullOrEmpty()) {
        LOGGER.error("'accessToken' must be set for RollbarLog4j2Appender")
        isNoopLogger = true
      }

      val rollbarEnvironment = if (environment.isNullOrEmpty()) {
        LOGGER.warn("Defaulting 'environment' to 'production'")
        "production"
      } else {
        environment
      }

      val appenderLayout = layout ?: PatternLayout.createDefaultLayout()

      if (!isNoopLogger) {
        val config = withAccessToken(accessToken)
          .environment(rollbarEnvironment)

        try {
          val hostName = InetAddress.getLocalHost().hostName
          config.server(Server.Builder().host(hostName)::build)
        } catch (e: UnknownHostException) {
          LOGGER.error("unable to get hostname", e)
        } catch (e: IllegalArgumentException) {
          LOGGER.error("unable to get hostname", e)
        }

        config.handleUncaughtErrors(true)
        val rollbar = Rollbar.init(config.build())
        appender = RollbarLog4j2Appender(name, filter, appenderLayout, false, rollbar)
      }

      return appender
    }
  }

  override fun append(event: LogEvent) {
    val rollbarLevel: RollbarLevel = if (event.level === Level.INFO) {
      RollbarLevel.INFO
    } else if (event.level === Level.TRACE || event.level === Level.DEBUG) {
      RollbarLevel.DEBUG
    } else if (event.level === Level.WARN) {
      RollbarLevel.WARNING
    } else if (event.level === Level.ERROR) {
      RollbarLevel.ERROR
    } else if (event.level === Level.FATAL) {
      RollbarLevel.CRITICAL
    } else {
      return
    }

    if (event.thrown != null) {
      if (event.message != null) {
        val formattedMessage = String(layout.toByteArray(event))
        this.client.log(event.thrown, formattedMessage, rollbarLevel)
      } else {
        this.client.log(event.thrown, rollbarLevel)
      }
    } else {
      val formattedMessage = String(layout.toByteArray(event))
      this.client.log(formattedMessage, rollbarLevel)
    }
  }
}
