package net.pieroxy.mom.utils.logging;

import java.util.logging.Level;

/**
 * Translates the log level as written in config.json (DEBUG/INFO/WARNING/ERROR) into a
 * java.util.logging.Level. java.util.logging has no native DEBUG level: it's mapped to FINE.
 */
public final class LogLevels {
  private LogLevels() {}

  public static Level parse(String configured, Level defaultLevel) {
    if (configured == null || configured.isBlank()) return defaultLevel;
    switch (configured.trim().toUpperCase()) {
      case "DEBUG": return Level.FINE;
      case "INFO": return Level.INFO;
      case "WARNING":
      case "WARN": return Level.WARNING;
      case "ERROR":
      case "SEVERE": return Level.SEVERE;
      default: return defaultLevel;
    }
  }
}
