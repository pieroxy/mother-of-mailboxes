package net.pieroxy.mom.diagnostics;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A test message as the SMTP sink received it, with what its own trace headers (see the sink's
 * {@code SmtpSession}) say about how it got there: they're the topmost {@code Return-Path} and
 * {@code Received}, prepended by the sink itself, so nothing the sending side wrote can fake them.
 *
 * @param clientIp the IP of the server that delivered the message to the sink.
 * @param helo the name that server gave in its HELO/EHLO.
 * @param envelopeFrom the SMTP MAIL FROM ("" for a null sender), the address SPF checks.
 * @param tls the TLS protocol and cipher used for that delivery, or null if it wasn't encrypted.
 * @param delayMs how long after it was sent the message was found on the sink.
 */
public record ReceivedTestMessage(byte[] raw, String clientIp, String helo, String envelopeFrom, String tls, long delayMs) {
  // "Received: from <helo> ([<ip>] or [IPv6:<ip>]) by <host> (MOM SMTP sink) with <proto> [(<tls>)] ...", unfolded.
  private static final Pattern SINK_RECEIVED = Pattern.compile(
      "^Received: from (\\S+) \\(\\[(?:IPv6:)?([^\\]]+)\\]\\) by \\S+ \\(MOM SMTP sink\\) with (\\S+)(?: \\(([^)]*)\\))?");
  private static final Pattern RETURN_PATH = Pattern.compile("^Return-Path: <([^>]*)>");

  /** @throws IllegalArgumentException if the message doesn't start with the sink's trace headers. */
  public static ReceivedTestMessage parse(byte[] raw, long delayMs) {
    String text = new String(raw, StandardCharsets.ISO_8859_1);
    int end = text.indexOf("\r\n\r\n");
    String[] headers = (end < 0 ? text : text.substring(0, end)).replaceAll("\r\n[ \t]+", " ").split("\r\n");
    if (headers.length < 2) throw new IllegalArgumentException("The SMTP sink returned a message without its trace headers.");
    Matcher returnPath = RETURN_PATH.matcher(headers[0]);
    Matcher received = SINK_RECEIVED.matcher(headers[1]);
    if (!returnPath.find() || !received.find()) {
      throw new IllegalArgumentException("The SMTP sink returned a message without its trace headers.");
    }
    return new ReceivedTestMessage(raw, received.group(2), received.group(1), returnPath.group(1), received.group(4), delayMs);
  }

  /** The domain of {@link #envelopeFrom}, or null for a null sender. */
  public String envelopeDomain() {
    int at = envelopeFrom.lastIndexOf('@');
    return at < 0 || at == envelopeFrom.length() - 1 ? null : envelopeFrom.substring(at + 1).toLowerCase(java.util.Locale.ROOT);
  }
}
