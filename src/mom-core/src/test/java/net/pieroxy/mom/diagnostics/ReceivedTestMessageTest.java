package net.pieroxy.mom.diagnostics;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class ReceivedTestMessageTest {
  private static ReceivedTestMessage parse(String message) {
    return ReceivedTestMessage.parse(message.getBytes(StandardCharsets.ISO_8859_1), 1234);
  }

  @Test
  public void readsTheSinkTraceHeaders() {
    ReceivedTestMessage m = parse("Return-Path: <me@Example.org>\r\n"
        + "Received: from out.example.org ([203.0.113.7])\r\n"
        + "\tby sink.test (MOM SMTP sink) with ESMTPS (TLSv1.3:TLS_AES_256_GCM_SHA384)\r\n"
        + "\tfor <mom-test@sink.test>; Sun, 18 Oct 2026 10:00:00 +0200\r\n"
        + "Received: from laptop by out.example.org; Sun, 18 Oct 2026 10:00:00 +0200\r\n"
        + "Subject: 5bd81a15-9ce3-4f44-9f93-0c7a14604507\r\n\r\nhello\r\n");
    assertEquals("203.0.113.7", m.clientIp());
    assertEquals("out.example.org", m.helo());
    assertEquals("me@Example.org", m.envelopeFrom());
    assertEquals("example.org", m.envelopeDomain());
    assertEquals("TLSv1.3:TLS_AES_256_GCM_SHA384", m.tls());
    assertEquals(1234, m.delayMs());
  }

  @Test
  public void plainTextIpv6AndNullSender() {
    ReceivedTestMessage m = parse("Return-Path: <>\r\n"
        + "Received: from out.example.org ([IPv6:2001:db8::7])\r\n"
        + "\tby sink.test (MOM SMTP sink) with ESMTP\r\n"
        + "\tfor <mom-test@sink.test>; Sun, 18 Oct 2026 10:00:00 +0200\r\n\r\nhello\r\n");
    assertEquals("2001:db8::7", m.clientIp());
    assertNull(m.tls());
    assertEquals("", m.envelopeFrom());
    assertNull(m.envelopeDomain());
  }

  @Test
  public void refusesAMessageWithoutTheSinkHeaders() {
    try {
      parse("Received: from x ([1.2.3.4]) by elsewhere; date\r\nSubject: hi\r\n\r\nbody");
      fail("should have refused it");
    } catch (IllegalArgumentException expected) {
      // ok
    }
  }
}
