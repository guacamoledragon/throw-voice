package org.slf4j.impl;

import org.slf4j.ILoggerFactory;
import org.slf4j.spi.LoggerFactoryBinder;

public class StaticLoggerBinder implements LoggerFactoryBinder {

  private static final StaticLoggerBinder SINGLETON = new StaticLoggerBinder();

  public static final StaticLoggerBinder getSingleton() {
    return SINGLETON;
  }

  public static String REQUESTED_API_VERSION = "1.7.25";

  private static final String loggerFactoryClassStr = RollbarLoggerFactory.class.getName();

  private final ILoggerFactory loggerFactory;

  private StaticLoggerBinder() {
    loggerFactory = new RollbarLoggerFactory();
  }

  public ILoggerFactory getLoggerFactory() {
    return loggerFactory;
  }

  public String getLoggerFactoryClassStr() {
    return loggerFactoryClassStr;
  }
}

