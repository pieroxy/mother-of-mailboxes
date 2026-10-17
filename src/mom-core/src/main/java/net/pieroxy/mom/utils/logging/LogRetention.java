package net.pieroxy.mom.utils.logging;

/** How many days of log files to keep, from the top-level {@code keepLogFiles} setting. */
public final class LogRetention {
  public static final int DEFAULT_DAYS = 30;
  /** Rotate daily, never delete. */
  public static final int FOREVER = 0;

  private LogRetention() {}

  /** Unset: {@link #DEFAULT_DAYS}; 0: {@link #FOREVER}; N: N days. */
  public static int days(Integer keepLogFiles) {
    return keepLogFiles == null ? DEFAULT_DAYS : keepLogFiles;
  }

  public static void validate(Integer keepLogFiles) {
    if (keepLogFiles != null && keepLogFiles < 0) {
      throw new IllegalArgumentException("Keep log files must be empty (30 days), 0 (forever) or a positive number of days.");
    }
  }
}
