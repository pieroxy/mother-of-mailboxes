package net.pieroxy.mom.sink;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

/**
 * Reads CRLF (or bare LF) terminated lines as raw bytes, bounded in memory: past {@code max}
 * bytes, the rest of the line is read and discarded, and the line is flagged as overflowing.
 */
class LineReader {
  private final BufferedInputStream in;

  LineReader(InputStream in) {
    this.in = new BufferedInputStream(in);
  }

  record Line(byte[] bytes, boolean overflow) {
    String asString() {
      return new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
    }
  }

  /** @return the line without its terminator, or null at end of stream. */
  Line read(int max) throws IOException {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    boolean overflow = false;
    int b;
    while ((b = in.read()) != -1) {
      if (b == '\n') {
        byte[] bytes = buf.toByteArray();
        int len = bytes.length;
        if (!overflow && len > 0 && bytes[len - 1] == '\r') {
          bytes = java.util.Arrays.copyOf(bytes, len - 1);
        }
        return new Line(bytes, overflow);
      }
      if (buf.size() < max) {
        buf.write(b);
      } else {
        overflow = true;
      }
    }
    return null;
  }

  /** Whether the client already sent bytes we haven't read (forbidden right after STARTTLS — see RFC 3207 §4.2). */
  boolean hasPendingInput() throws IOException {
    return in.available() > 0;
  }
}
