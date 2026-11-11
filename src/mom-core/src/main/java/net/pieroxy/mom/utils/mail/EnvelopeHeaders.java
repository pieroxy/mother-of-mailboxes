package net.pieroxy.mom.utils.mail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Validation of an account's envelope headers: see {@code MailAccountConfiguration#getEnvelopeHeaders}. */
public final class EnvelopeHeaders {
  private EnvelopeHeaders() {
  }

  /**
   * Trimmed, and deduplicated ignoring case (the first spelling is kept); blank entries are dropped.
   *
   * @throws IllegalArgumentException on an entry that isn't a valid header name (RFC 5322 §3.6.8).
   */
  public static List<String> normalize(List<String> headers) {
    List<String> normalized = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String header : headers != null ? headers : List.<String>of()) {
      String trimmed = header != null ? header.trim() : "";
      if (trimmed.isEmpty()) continue;
      if (!trimmed.matches("[!-9;-~]+")) {
        throw new IllegalArgumentException("\"" + header + "\" is not a valid header name.");
      }
      if (seen.add(trimmed.toLowerCase(Locale.ROOT))) normalized.add(trimmed);
    }
    return normalized;
  }
}
