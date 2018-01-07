package org.slf4j.impl;

import com.rollbar.notifier.Rollbar;
import com.rollbar.notifier.config.Config;
import org.slf4j.helpers.Util;
import org.slf4j.impl.OutputChoice.OutputChoiceType;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Properties;

import static com.rollbar.notifier.config.ConfigBuilder.withAccessToken;

/**
 * This class holds configuration values for {@link RollbarLogger}. The
 * values are computed at runtime. See {@link RollbarLogger} documentation for
 * more information.
 *
 *
 * @author Ceki G&uuml;lc&uuml;
 * @author Scott Sanders
 * @author Rod Waldhoff
 * @author Robert Burrell Donkin
 * @author C&eacute;drik LIME
 *
 * @since 1.7.25
 */
public class RollbarLoggerConfiguration {

  private static final String CONFIGURATION_FILE = "rollbarlogger.properties";

  static int DEFAULT_LOG_LEVEL_DEFAULT = RollbarLogger.LOG_LEVEL_INFO;
  int defaultLogLevel = DEFAULT_LOG_LEVEL_DEFAULT;

  private static final boolean SHOW_DATE_TIME_DEFAULT = false;
  boolean showDateTime = SHOW_DATE_TIME_DEFAULT;

  private static final String DATE_TIME_FORMAT_STR_DEFAULT = null;
  private static String dateTimeFormatStr = DATE_TIME_FORMAT_STR_DEFAULT;

  DateFormat dateFormatter = null;

  private static final boolean SHOW_THREAD_NAME_DEFAULT = true;
  boolean showThreadName = SHOW_THREAD_NAME_DEFAULT;

  final static boolean SHOW_LOG_NAME_DEFAULT = true;
  boolean showLogName = SHOW_LOG_NAME_DEFAULT;

  private static final boolean SHOW_SHORT_LOG_NAME_DEFAULT = false;
  boolean showShortLogName = SHOW_SHORT_LOG_NAME_DEFAULT;

  private static final boolean LEVEL_IN_BRACKETS_DEFAULT = false;
  boolean levelInBrackets = LEVEL_IN_BRACKETS_DEFAULT;

  private static String LOG_FILE_DEFAULT = "System.err";
  private String logFile = LOG_FILE_DEFAULT;
  OutputChoice outputChoice = null;

  private static final boolean CACHE_OUTPUT_STREAM_DEFAULT = false;
  private boolean cacheOutputStream = CACHE_OUTPUT_STREAM_DEFAULT;

  private static final String WARN_LEVELS_STRING_DEFAULT = "WARN";
  String warnLevelString = WARN_LEVELS_STRING_DEFAULT;

  private static final String ROLLBAR_ENVIRONMENT_DEFAULT = "production";
  String rollbarEnvironment = ROLLBAR_ENVIRONMENT_DEFAULT;

  String rollbarAccessToken = null;

  Rollbar rollbar;

  private final Properties properties = new Properties();

  void init() {
    loadProperties();

    String defaultLogLevelString = getStringProperty(RollbarLogger.DEFAULT_LOG_LEVEL_KEY, null);
    if (defaultLogLevelString != null)
      defaultLogLevel = stringToLevel(defaultLogLevelString);

    showLogName = getBooleanProperty(RollbarLogger.SHOW_LOG_NAME_KEY, RollbarLoggerConfiguration.SHOW_LOG_NAME_DEFAULT);
    showShortLogName = getBooleanProperty(RollbarLogger.SHOW_SHORT_LOG_NAME_KEY, SHOW_SHORT_LOG_NAME_DEFAULT);
    showDateTime = getBooleanProperty(RollbarLogger.SHOW_DATE_TIME_KEY, SHOW_DATE_TIME_DEFAULT);
    showThreadName = getBooleanProperty(RollbarLogger.SHOW_THREAD_NAME_KEY, SHOW_THREAD_NAME_DEFAULT);
    dateTimeFormatStr = getStringProperty(RollbarLogger.DATE_TIME_FORMAT_KEY, DATE_TIME_FORMAT_STR_DEFAULT);
    levelInBrackets = getBooleanProperty(RollbarLogger.LEVEL_IN_BRACKETS_KEY, LEVEL_IN_BRACKETS_DEFAULT);
    warnLevelString = getStringProperty(RollbarLogger.WARN_LEVEL_STRING_KEY, WARN_LEVELS_STRING_DEFAULT);
    rollbarEnvironment = getStringProperty(RollbarLogger.ROLLBAR_ENVIRONMENT_KEY, ROLLBAR_ENVIRONMENT_DEFAULT);
    rollbarAccessToken = getStringProperty(RollbarLogger.ROLLBAR_ACCESS_TOKEN_KEY);

    logFile = getStringProperty(RollbarLogger.LOG_FILE_KEY, logFile);

    cacheOutputStream = getBooleanProperty(RollbarLogger.CACHE_OUTPUT_STREAM_STRING_KEY, CACHE_OUTPUT_STREAM_DEFAULT);
    outputChoice = computeOutputChoice(logFile, cacheOutputStream);

    if (dateTimeFormatStr != null) {
      try {
        dateFormatter = new SimpleDateFormat(dateTimeFormatStr);
      } catch (IllegalArgumentException e) {
        Util.report("Bad date format in " + CONFIGURATION_FILE + "; will output relative time", e);
      }
    }

    if (rollbarAccessToken != null) {
      Config config = withAccessToken(rollbarAccessToken)
        .environment(rollbarEnvironment)
        .build();

      rollbar = Rollbar.init(config);
      Util.report("Rollbar logger initialized.");
    }
  }

  private void loadProperties() {
    // Add props from the resource rollbarlogger.properties
    InputStream in = AccessController.doPrivileged(new PrivilegedAction<InputStream>() {
      public InputStream run() {
        ClassLoader threadCL = Thread.currentThread().getContextClassLoader();
        if (threadCL != null) {
          return threadCL.getResourceAsStream(CONFIGURATION_FILE);
        } else {
          return ClassLoader.getSystemResourceAsStream(CONFIGURATION_FILE);
        }
      }
    });
    if (null != in) {
      try {
        properties.load(in);
      } catch (java.io.IOException e) {
        // ignored
      } finally {
        try {
          in.close();
        } catch (java.io.IOException e) {
          // ignored
        }
      }
    }
  }

  String getStringProperty(String name, String defaultValue) {
    String prop = getStringProperty(name);
    return (prop == null) ? defaultValue : prop;
  }

  boolean getBooleanProperty(String name, boolean defaultValue) {
    String prop = getStringProperty(name);
    return (prop == null) ? defaultValue : "true".equalsIgnoreCase(prop);
  }

  String getStringProperty(String name) {
    String prop = null;
    try {
      prop = System.getProperty(name);
    } catch (SecurityException e) {
      ; // Ignore
    }
    return (prop == null) ? properties.getProperty(name) : prop;
  }

  static int stringToLevel(String levelStr) {
    if ("trace".equalsIgnoreCase(levelStr)) {
      return RollbarLogger.LOG_LEVEL_TRACE;
    } else if ("debug".equalsIgnoreCase(levelStr)) {
      return RollbarLogger.LOG_LEVEL_DEBUG;
    } else if ("info".equalsIgnoreCase(levelStr)) {
      return RollbarLogger.LOG_LEVEL_INFO;
    } else if ("warn".equalsIgnoreCase(levelStr)) {
      return RollbarLogger.LOG_LEVEL_WARN;
    } else if ("error".equalsIgnoreCase(levelStr)) {
      return RollbarLogger.LOG_LEVEL_ERROR;
    } else if ("off".equalsIgnoreCase(levelStr)) {
      return RollbarLogger.LOG_LEVEL_OFF;
    }
    // assume INFO by default
    return RollbarLogger.LOG_LEVEL_INFO;
  }

  private static OutputChoice computeOutputChoice(String logFile, boolean cacheOutputStream) {
    if ("System.err".equalsIgnoreCase(logFile))
      if (cacheOutputStream)
        return new OutputChoice(OutputChoiceType.CACHED_SYS_ERR);
      else
        return new OutputChoice(OutputChoiceType.SYS_ERR);
    else if ("System.out".equalsIgnoreCase(logFile)) {
      if (cacheOutputStream)
        return new OutputChoice(OutputChoiceType.CACHED_SYS_OUT);
      else
        return new OutputChoice(OutputChoiceType.SYS_OUT);
    } else {
      try {
        FileOutputStream fos = new FileOutputStream(logFile);
        PrintStream printStream = new PrintStream(fos);
        return new OutputChoice(printStream);
      } catch (FileNotFoundException e) {
        Util.report("Could not open [" + logFile + "]. Defaulting to System.err", e);
        return new OutputChoice(OutputChoiceType.SYS_ERR);
      }
    }
  }

}
