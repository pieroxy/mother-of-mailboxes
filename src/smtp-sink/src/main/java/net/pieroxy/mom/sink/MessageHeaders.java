package net.pieroxy.mom.sink;

import java.nio.charset.StandardCharsets;

/** Just enough RFC 5322 header parsing to find a message's Subject. */
final class MessageHeaders {
  private MessageHeaders() {}

  /** @return the unfolded, trimmed Subject of the message, or null if it has none. */
  static String subject(byte[] message) {
    String text = new String(message, StandardCharsets.ISO_8859_1);
    String[] lines = text.split("\r?\n", -1);
    String current = null;
    for (String line : lines) {
      if (line.isEmpty()) break; // End of the header section.
      if ((line.charAt(0) == ' ' || line.charAt(0) == '\t') && current != null) {
        current += line; // Folded continuation of the previous header.
        continue;
      }
      String found = subjectOf(current);
      if (found != null) return found;
      current = line;
    }
    return subjectOf(current);
  }

  private static String subjectOf(String header) {
    if (header == null) return null;
    int colon = header.indexOf(':');
    if (colon < 0 || !header.substring(0, colon).trim().equalsIgnoreCase("Subject")) return null;
    return header.substring(colon + 1).trim();
  }
}
