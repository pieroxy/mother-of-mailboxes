package net.pieroxy.mom.logging;

import net.pieroxy.mom.utils.logging.LogLevels;
import org.junit.Test;

import java.util.logging.Level;

import static org.junit.Assert.assertEquals;

public class LogLevelsTest {

  @Test
  public void mapsKnownLevelNamesCaseInsensitively() {
    assertEquals(Level.FINE, LogLevels.parse("DEBUG", Level.WARNING));
    assertEquals(Level.FINE, LogLevels.parse("debug", Level.WARNING));
    assertEquals(Level.INFO, LogLevels.parse("INFO", Level.WARNING));
    assertEquals(Level.WARNING, LogLevels.parse("WARNING", Level.INFO));
    assertEquals(Level.WARNING, LogLevels.parse("warn", Level.INFO));
    assertEquals(Level.SEVERE, LogLevels.parse("ERROR", Level.INFO));
    assertEquals(Level.SEVERE, LogLevels.parse("SEVERE", Level.INFO));
  }

  @Test
  public void fallsBackToDefaultWhenMissingOrUnknown() {
    assertEquals(Level.WARNING, LogLevels.parse(null, Level.WARNING));
    assertEquals(Level.WARNING, LogLevels.parse("", Level.WARNING));
    assertEquals(Level.WARNING, LogLevels.parse("not-a-level", Level.WARNING));
  }
}
