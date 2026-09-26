package net.pieroxy.mom.utils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Validates that a string is safe to use as a single filesystem path component (a file or
 * directory name, not a path — no separators) on ext4, NTFS and macOS (APFS/HFS+) alike, the
 * three filesystems MOM's data folder is known to run on. Used for config values that get turned
 * directly into file/directory names, such as a mail account's {@code displayName} (see
 * {@code MailAccountConfiguration}, {@code MailAccountStateStore}, {@code LearnedRulesStore},
 * {@code ClassifierCorpusStore} and {@code MailAccount}'s per-account stats directory).
 * <p>
 * ext4 itself only forbids {@code /} and NUL, and macOS only forbids {@code /} and NUL at the
 * filesystem level — but this validates against the strictest of the three (NTFS's rules, plus
 * the colon Finder itself refuses) so that one displayName stays valid no matter which of the
 * three the data folder ends up on, including a case migrating from one to another later.
 */
public final class FileNameValidator {
  // NTFS's forbidden characters. ':' is also refused by macOS's Finder (the path separator in
  // the old HFS classic API), even though the APFS/HFS+ filesystem itself allows it.
  private static final String FORBIDDEN_CHARS = "<>:\"/\\|?*";
  // Windows reserved device names — reserved with or without an extension (e.g. "COM1.txt").
  private static final Set<String> RESERVED_NAMES = Set.of(
      "CON", "PRN", "AUX", "NUL",
      "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
      "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");
  // The lowest common denominator across ext4 (255 bytes), NTFS and HFS+/APFS (255 UTF-16 units,
  // i.e. never fewer bytes than that in UTF-8) — comparing UTF-8 byte length stays on the safe
  // side for all three.
  private static final int MAX_BYTES = 255;

  private FileNameValidator() {}

  /**
   * @param context human-readable description of the value, used in the error message (e.g.
   *                {@code "mail account displayName"}).
   * @throws IllegalArgumentException if {@code name} would not be a valid file/directory name on
   *                                   ext4, NTFS or macOS.
   */
  public static void validate(String name, String context) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException(context + " must not be blank.");
    }
    if (name.equals(".") || name.equals("..")) {
      throw new IllegalArgumentException(context + " must not be \"" + name + "\".");
    }
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c < 0x20 || FORBIDDEN_CHARS.indexOf(c) >= 0) {
        throw new IllegalArgumentException(context + " (\"" + name + "\") contains the character "
                + charDescription(c) + ", which is not allowed in a file name on ext4, NTFS or macOS.");
      }
    }
    char last = name.charAt(name.length() - 1);
    if (last == ' ' || last == '.') {
      throw new IllegalArgumentException(context + " (\"" + name + "\") must not end with a space or a period"
              + " — NTFS silently strips trailing spaces/periods, so the file actually created would not match.");
    }
    String baseName = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
    if (RESERVED_NAMES.contains(baseName.toUpperCase(Locale.ROOT))) {
      throw new IllegalArgumentException(context + " (\"" + name + "\") is a reserved device name on Windows/NTFS.");
    }
    int byteLength = name.getBytes(StandardCharsets.UTF_8).length;
    if (byteLength > MAX_BYTES) {
      throw new IllegalArgumentException(context + " (\"" + name + "\") is " + byteLength
              + " bytes long, over the " + MAX_BYTES + "-byte file name limit shared by ext4, NTFS and macOS.");
    }
  }

  private static String charDescription(char c) {
    return c < 0x20 ? "0x" + Integer.toHexString(c) : "'" + c + "'";
  }
}
