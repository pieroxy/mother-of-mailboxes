package net.pieroxy.mom.detection.lookalike;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Cyrillic and Greek letters that look like a plain Latin letter ({@code а} → {@code a}, {@code Р}
 * → {@code P}…), taken from Unicode's {@code confusables.txt} (UTS #39), bundled under
 * {@code /unicode/} — refreshed by {@code updateUnicodeData.sh}. Loaded once, on first use.
 */
final class LatinLookalikes {
  private static final String RESOURCE = "/unicode/confusables.txt";

  private LatinLookalikes() {}

  private static final class Holder {
    static final Map<Integer, Character> TABLE = load();
  }

  /** The ASCII letter {@code codePoint} looks like, or null if it isn't a Cyrillic or Greek look-alike of one. */
  static Character latinOf(int codePoint) {
    return Holder.TABLE.get(codePoint);
  }

  /** Each line: {@code source ; target code points ; MA # comment}. Only single-ASCII-letter targets are kept. */
  private static Map<Integer, Character> load() {
    Map<Integer, Character> table = new HashMap<>();
    try (InputStream in = LatinLookalikes.class.getResourceAsStream(RESOURCE)) {
      if (in == null) throw new IllegalStateException(RESOURCE + " is missing from the classpath");
      BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
      String line;
      while ((line = reader.readLine()) != null) {
        int hash = line.indexOf('#');
        String data = (hash < 0 ? line : line.substring(0, hash)).replace("﻿", "").trim();
        if (data.isEmpty()) continue;
        String[] fields = data.split(";");
        if (fields.length < 2) continue;
        String[] target = fields[1].trim().split("\\s+");
        if (target.length != 1) continue;
        int source = Integer.parseInt(fields[0].trim(), 16);
        int latin = Integer.parseInt(target[0], 16);
        boolean asciiLetter = (latin >= 'a' && latin <= 'z') || (latin >= 'A' && latin <= 'Z');
        Character.UnicodeScript script = Character.UnicodeScript.of(source);
        if (asciiLetter && (script == Character.UnicodeScript.CYRILLIC || script == Character.UnicodeScript.GREEK)) {
          table.put(source, (char) latin);
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return Map.copyOf(table);
  }
}
