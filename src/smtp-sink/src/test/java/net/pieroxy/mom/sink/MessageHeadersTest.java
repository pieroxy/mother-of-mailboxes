package net.pieroxy.mom.sink;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MessageHeadersTest {
  private static String subject(String message) {
    return MessageHeaders.subject(message.getBytes(StandardCharsets.ISO_8859_1));
  }

  @Test
  public void findsSubjectAmongHeaders() {
    assertEquals("abc", subject("From: a@b\r\nsubject:   abc  \r\nTo: c@d\r\n\r\nbody"));
  }

  @Test
  public void unfoldsFoldedSubject() {
    assertEquals("abc\tdef", subject("Subject: abc\r\n\tdef\r\nTo: c@d\r\n\r\nbody"));
  }

  @Test
  public void subjectAsLastHeader() {
    assertEquals("abc", subject("From: a@b\nSubject: abc\n\nbody"));
  }

  @Test
  public void ignoresSubjectInBody() {
    assertNull(subject("From: a@b\r\n\r\nSubject: abc\r\n"));
  }
}
