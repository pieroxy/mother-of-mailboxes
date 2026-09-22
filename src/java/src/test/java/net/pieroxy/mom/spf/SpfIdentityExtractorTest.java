package net.pieroxy.mom.spf;

import net.pieroxy.mom.detection.spf.SpfIdentityExtractor;
import org.junit.Test;

import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SpfIdentityExtractorTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  @Test
  public void extractsIpFromTypicalReceivedHeader() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addHeader("Received", "from mail-sor-f42.google.com (mail-sor-f42.google.com. [209.85.167.42])\n"
            + "\tby mx.example.com with SMTPS id abc123 for <me@example.com>; Mon, 31 Aug 2026 10:00:00 +0000");

    assertEquals(Optional.of("209.85.167.42"), SpfIdentityExtractor.extractClientIp(message));
  }

  @Test
  public void extractsIpv6WithPrefixFromReceivedHeader() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addHeader("Received", "from mail.example.net (mail.example.net [IPv6:2001:db8::1])\n"
            + "\tby mx.example.com with ESMTPS id xyz; Mon, 31 Aug 2026 10:00:00 +0000");

    assertEquals(Optional.of("2001:db8::1"), SpfIdentityExtractor.extractClientIp(message));
  }

  @Test
  public void usesOnlyTheTopmostReceivedHeader() throws Exception {
    // Just like a real message received over IMAP: the most recent Received header (added by
    // our own server) sits physically at the top of the raw message, and getHeader() preserves
    // that order for a message parsed from its raw bytes (unlike programmatic addHeader()
    // calls, which stack in reverse order).
    String raw = "Received: from relay.example.com (relay.example.com [203.0.113.9]) by mx.example.com with ESMTP id 1\r\n"
            + "Received: from spoofed.example.org (spoofed.example.org [10.0.0.1]) by relay.example.com with ESMTP id 2\r\n"
            + "Subject: test\r\n\r\nbody\r\n";
    MimeMessage message = new MimeMessage(session, new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));

    assertEquals(Optional.of("203.0.113.9"), SpfIdentityExtractor.extractClientIp(message));
  }

  @Test
  public void noReceivedHeaderYieldsEmpty() throws Exception {
    MimeMessage message = new MimeMessage(session);

    assertEquals(Optional.empty(), SpfIdentityExtractor.extractClientIp(message));
  }

  @Test
  public void extractsDomainFromReturnPathInPreference() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addHeader("Return-Path", "<bounce+id@bounces.example.net>");
    message.setFrom(new InternetAddress("someone@other-domain.com"));

    assertEquals(Optional.of("bounces.example.net"), SpfIdentityExtractor.extractSenderDomain(message));
  }

  @Test
  public void fallsBackToFromWhenNoReturnPath() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("someone@example.com"));

    assertEquals(Optional.of("example.com"), SpfIdentityExtractor.extractSenderDomain(message));
  }

  @Test
  public void fallsBackToFromWhenReturnPathIsEmptyBounceAddress() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addHeader("Return-Path", "<>");
    message.setFrom(new InternetAddress("someone@example.com"));

    assertEquals(Optional.of("example.com"), SpfIdentityExtractor.extractSenderDomain(message));
  }

  @Test
  public void noReturnPathAndNoFromYieldsEmpty() throws Exception {
    MimeMessage message = new MimeMessage(session);

    assertTrue(SpfIdentityExtractor.extractSenderDomain(message).isEmpty());
  }

  @Test
  public void extractClientIpWithExplicitLoggerBehavesLikeDefault() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addHeader("Received", "from mail.example.com (mail.example.com [203.0.113.10]) by mx.example.com with ESMTP id 1");

    assertEquals(Optional.of("203.0.113.10"), SpfIdentityExtractor.extractClientIp(message, Logger.getLogger("test")));
  }

  @Test
  public void extractSenderDomainWithExplicitLoggerBehavesLikeDefault() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("someone@example.com"));

    assertEquals(Optional.of("example.com"), SpfIdentityExtractor.extractSenderDomain(message, Logger.getLogger("test")));
  }
}
