package net.pieroxy.mom.detection.classifier;

import net.pieroxy.mom.detection.classifier.ClassifierExample;
import net.pieroxy.mom.detection.classifier.ClassifierExampleExtractor;
import net.pieroxy.mom.detection.classifier.ClassifierLabel;
import org.junit.Test;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ClassifierExampleExtractorTest {
  private final Session session = Session.getDefaultInstance(new Properties());

  @Test
  public void extractsSubjectFromToDateAndLabel() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("alice@example.com", "Alice"));
    message.setRecipients(Message.RecipientType.TO, new InternetAddress[]{
        new InternetAddress("bob@example.com"), new InternetAddress("carol@example.com")
    });
    message.setSubject("Buy now!!!");
    Date sentDate = new Date(1_700_000_000_000L);
    message.setSentDate(sentDate);

    Instant fetchDate = Instant.ofEpochMilli(1_800_000_000_000L);
    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.SPAM, fetchDate);

    assertEquals("Buy now!!!", example.getSubject());
    assertEquals(ClassifierLabel.SPAM, example.getLabel());
    assertEquals(1, example.getFrom().size());
    assertEquals("alice@example.com", example.getFrom().get(0));
    assertEquals(2, example.getTo().size());
    assertTrue(example.getTo().contains("bob@example.com"));
    assertTrue(example.getTo().contains("carol@example.com"));
    assertEquals(sentDate.toInstant().toString(), example.getMailDate());
    assertEquals(fetchDate.toString(), example.getFetchDate());
    assertEquals("Alice", example.getFromDisplayName());
    assertNull(example.getToDisplayName()); // bob/carol have no display name
  }

  @Test
  public void joinsMultipleDisplayNamesWithASpace() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("alice@example.com", "Alice"));
    message.setRecipients(Message.RecipientType.TO, new InternetAddress[]{
        new InternetAddress("bob@example.com", "Bob"), new InternetAddress("carol@example.com", "Carol")
    });

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.HAM, Instant.now());

    assertEquals("Alice", example.getFromDisplayName());
    assertEquals("Bob Carol", example.getToDisplayName());
  }

  @Test
  public void blankDisplayNameIsTreatedAsAbsent() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("alice@example.com", ""));

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.HAM, Instant.now());

    assertNull(example.getFromDisplayName());
  }

  @Test
  public void toleratesMissingFromToSubjectAndDate() throws Exception {
    MimeMessage message = new MimeMessage(session);

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.HAM, Instant.now());

    assertTrue(example.getFrom().isEmpty());
    assertTrue(example.getTo().isEmpty());
    assertNull(example.getSubject());
    assertNull(example.getMailDate());
    assertNull(example.getIp());
    assertNull(example.getFromDisplayName());
    assertNull(example.getToDisplayName());
    assertFalse(example.isReply());
    assertNull(example.getPrecedence());
    assertNull(example.getListId());
    assertFalse(example.isListUnsubscribePresent());
    assertNull(example.getReturnPathDomain());
    assertNull(example.getReturnPathMismatch());
    assertNull(example.getReplyToDomain());
    assertNull(example.getReplyToMismatch());
    assertNull(example.getMessageId());
    assertTrue(example.getAttachmentExtensions().isEmpty());
    // A plain, locally-built message has no INTERNALDATE (that's an IMAP server attribute) —
    // only a real IMAPMessage populates this; see ClassifierCorpusScannerTest for that path.
    assertNull(example.getReceivedDate());
  }

  @Test
  public void extractsMessageId() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addHeader("Message-ID", "<abc123@example.com>");

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.HAM, Instant.now());

    assertEquals("<abc123@example.com>", example.getMessageId());
  }

  @Test
  public void detectsReplyViaInReplyToHeader() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addHeader("In-Reply-To", "<original-msg-id@example.com>");

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.HAM, Instant.now());

    assertTrue(example.isReply());
  }

  @Test
  public void detectsReplyViaReferencesHeaderAlone() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addHeader("References", "<msg1@example.com> <msg2@example.com>");

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.HAM, Instant.now());

    assertTrue(example.isReply());
  }

  @Test
  public void extractsPrecedenceAndListIdRawValues() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addHeader("Precedence", "bulk");
    message.addHeader("List-Id", "Weekly Newsletter <newsletter.example.com>");

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.HAM, Instant.now());

    assertEquals("bulk", example.getPrecedence());
    assertEquals("Weekly Newsletter <newsletter.example.com>", example.getListId());
  }

  @Test
  public void detectsListUnsubscribePresence() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addHeader("List-Unsubscribe", "<mailto:unsub@example.com>");

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.HAM, Instant.now());

    assertTrue(example.isListUnsubscribePresent());
  }

  @Test
  public void detectsReturnPathDomainMismatchFromFrom() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("alice@example.com"));
    message.addHeader("Return-Path", "<bounce@spammy.example.net>");

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.SPAM, Instant.now());

    assertEquals("spammy.example.net", example.getReturnPathDomain());
    assertEquals(Boolean.TRUE, example.getReturnPathMismatch());
  }

  @Test
  public void noReturnPathMismatchWhenDomainsAgree() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("alice@example.com"));
    message.addHeader("Return-Path", "<bounce@example.com>");

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.HAM, Instant.now());

    assertEquals(Boolean.FALSE, example.getReturnPathMismatch());
  }

  @Test
  public void emptyReturnPathIsTreatedAsAbsent() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("alice@example.com"));
    message.addHeader("Return-Path", "<>"); // bounce with no return address

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.HAM, Instant.now());

    assertNull(example.getReturnPathDomain());
    assertNull(example.getReturnPathMismatch());
  }

  @Test
  public void detectsReplyToDomainMismatchFromFrom() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setFrom(new InternetAddress("alice@example.com"));
    message.addHeader("Reply-To", "Support <support@spammy.example.net>");

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.SPAM, Instant.now());

    assertEquals("spammy.example.net", example.getReplyToDomain());
    assertEquals(Boolean.TRUE, example.getReplyToMismatch());
  }

  @Test
  public void mismatchIsNullRatherThanFalseWhenFromHasMultipleAddresses() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.addFrom(new InternetAddress[]{
        new InternetAddress("alice@example.com"), new InternetAddress("bob@example.com")
    });
    message.addHeader("Reply-To", "support@example.com");

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.HAM, Instant.now());

    assertEquals("example.com", example.getReplyToDomain());
    assertNull("undeterminable (multiple From), not 'no mismatch'", example.getReplyToMismatch());
  }

  @Test
  public void extractsIpFromTheLastReceivedHeaderClosestToTheOrigin() throws Exception {
    // Built from a raw stream (like a real IMAP fetch) rather than with addHeader(), which
    // prefixes instead of preserving message order: the most recent relay (ours) appears first
    // in a real message, the original sender's last.
    String raw = "Received: from relay.example.com (relay.example.com [198.51.100.9]) by mx\r\n"
        + "Received: from origin.example.net (origin.example.net [203.0.113.5]) by relay\r\n"
        + "Subject: test\r\n\r\nbody\r\n";
    MimeMessage message = new MimeMessage(session, new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.SPAM, Instant.now());

    assertEquals("203.0.113.5", example.getIp());
  }

  @Test
  public void returnsNullIpWhenNoReceivedHeader() throws Exception {
    MimeMessage message = new MimeMessage(session);

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.HAM, Instant.now());

    assertNull(example.getIp());
  }

  @Test
  public void noAttachmentsOnAPlainTextMessage() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setText("Hello");

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.HAM, Instant.now());

    assertTrue(example.getAttachmentExtensions().isEmpty());
  }

  @Test
  public void extractsAttachmentExtensionLowercased() throws Exception {
    MimeMessage message = new MimeMessage(session);
    MimeMultipart multipart = new MimeMultipart();
    MimeBodyPart body = new MimeBodyPart();
    body.setText("Please find attached");
    multipart.addBodyPart(body);
    MimeBodyPart attachment = new MimeBodyPart();
    attachment.setText("dummy content");
    attachment.setFileName("Invoice.PDF");
    multipart.addBodyPart(attachment);
    message.setContent(multipart);
    message.saveChanges();

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.SPAM, Instant.now());

    assertEquals(List.of("pdf"), example.getAttachmentExtensions());
  }

  @Test
  public void extractsOneExtensionPerAttachmentIncludingDuplicates() throws Exception {
    MimeMessage message = new MimeMessage(session);
    MimeMultipart multipart = new MimeMultipart();
    for (String filename : new String[]{"a.exe", "b.exe", "c.zip"}) {
      MimeBodyPart part = new MimeBodyPart();
      part.setText("dummy");
      part.setFileName(filename);
      multipart.addBodyPart(part);
    }
    message.setContent(multipart);
    message.saveChanges();

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.SPAM, Instant.now());

    assertEquals(3, example.getAttachmentExtensions().size());
    assertEquals(2, example.getAttachmentExtensions().stream().filter("exe"::equals).count());
    assertTrue(example.getAttachmentExtensions().contains("zip"));
  }

  @Test
  public void attachmentWithNoDotInFilenameGetsASentinelExtension() throws Exception {
    MimeMessage message = new MimeMessage(session);
    MimeMultipart multipart = new MimeMultipart();
    MimeBodyPart attachment = new MimeBodyPart();
    attachment.setText("dummy");
    attachment.setFileName("readme");
    multipart.addBodyPart(attachment);
    message.setContent(multipart);
    message.saveChanges();

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.SPAM, Instant.now());

    assertEquals(List.of("(none)"), example.getAttachmentExtensions());
  }

  @Test
  public void findsAttachmentsNestedInsideAnInnerMultipart() throws Exception {
    MimeMessage message = new MimeMessage(session);
    MimeMultipart outer = new MimeMultipart(); // multipart/mixed

    MimeMultipart alternative = new MimeMultipart("alternative"); // the text/plain+text/html body, no filenames
    MimeBodyPart plainText = new MimeBodyPart();
    plainText.setText("Hello");
    alternative.addBodyPart(plainText);
    MimeBodyPart html = new MimeBodyPart();
    html.setContent("<p>Hello</p>", "text/html");
    alternative.addBodyPart(html);
    MimeBodyPart alternativeWrapper = new MimeBodyPart();
    alternativeWrapper.setContent(alternative);
    outer.addBodyPart(alternativeWrapper);

    MimeBodyPart attachment = new MimeBodyPart();
    attachment.setText("dummy");
    attachment.setFileName("photo.png");
    outer.addBodyPart(attachment);

    message.setContent(outer);
    message.saveChanges();

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.HAM, Instant.now());

    assertEquals(List.of("png"), example.getAttachmentExtensions());
  }

  @Test
  public void extractsPlainTextBodyWhenThatsAllThereIs() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setText("Hello there");

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.HAM, Instant.now());

    assertEquals("Hello there", example.getBodyText());
    assertEquals("plain", example.getBodySource());
  }

  @Test
  public void prefersTheHtmlPartOverPlainTextWhenBothArePresent() throws Exception {
    MimeMessage message = new MimeMessage(session);
    MimeMultipart alternative = new MimeMultipart("alternative");
    MimeBodyPart plainText = new MimeBodyPart();
    plainText.setText("plain fallback nobody reads");
    alternative.addBodyPart(plainText);
    MimeBodyPart html = new MimeBodyPart();
    html.setContent("<html><body><p>Buy <b>now</b>!</p></body></html>", "text/html");
    alternative.addBodyPart(html);
    message.setContent(alternative);
    message.saveChanges();

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.SPAM, Instant.now());

    assertEquals("Buy now!", example.getBodyText());
    assertEquals("html", example.getBodySource());
  }

  @Test
  public void stripsScriptAndStyleContentAlongWithTheTags() throws Exception {
    MimeMessage message = new MimeMessage(session);
    message.setContent("<html><head><style>p{color:red}</style></head>"
        + "<body><script>alert('x')</script><p>Real content</p></body></html>", "text/html");
    message.saveChanges();

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.SPAM, Instant.now());

    assertEquals("Real content", example.getBodyText());
    assertEquals("html", example.getBodySource());
  }

  @Test
  public void findsTheHtmlPartNestedInsideAnOuterMultipart() throws Exception {
    MimeMessage message = new MimeMessage(session);
    MimeMultipart outer = new MimeMultipart(); // multipart/mixed

    MimeMultipart alternative = new MimeMultipart("alternative");
    MimeBodyPart plainText = new MimeBodyPart();
    plainText.setText("plain fallback");
    alternative.addBodyPart(plainText);
    MimeBodyPart html = new MimeBodyPart();
    html.setContent("<p>Hello world</p>", "text/html");
    alternative.addBodyPart(html);
    MimeBodyPart alternativeWrapper = new MimeBodyPart();
    alternativeWrapper.setContent(alternative);
    outer.addBodyPart(alternativeWrapper);

    MimeBodyPart attachment = new MimeBodyPart();
    attachment.setText("dummy");
    attachment.setFileName("photo.png");
    outer.addBodyPart(attachment);

    message.setContent(outer);
    message.saveChanges();

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.HAM, Instant.now());

    assertEquals("Hello world", example.getBodyText());
    assertEquals("html", example.getBodySource());
  }

  @Test
  public void anAttachmentNamedLikeAnHtmlFileIsNotMistakenForTheBody() throws Exception {
    MimeMessage message = new MimeMessage(session);
    MimeMultipart multipart = new MimeMultipart();
    MimeBodyPart body = new MimeBodyPart();
    body.setText("The real body");
    multipart.addBodyPart(body);
    MimeBodyPart attachment = new MimeBodyPart();
    attachment.setContent("<p>not the body</p>", "text/html");
    attachment.setFileName("newsletter.html");
    multipart.addBodyPart(attachment);
    message.setContent(multipart);
    message.saveChanges();

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.SPAM, Instant.now());

    assertEquals("The real body", example.getBodyText());
    assertEquals("plain", example.getBodySource());
  }

  @Test
  public void bodyTextIsNullForAnAttachmentOnlyMessage() throws Exception {
    MimeMessage message = new MimeMessage(session);
    MimeMultipart multipart = new MimeMultipart();
    MimeBodyPart attachment = new MimeBodyPart();
    attachment.setText("dummy");
    attachment.setFileName("invoice.pdf");
    multipart.addBodyPart(attachment);
    message.setContent(multipart);
    message.saveChanges();

    ClassifierExample example = ClassifierExampleExtractor.extract(message, ClassifierLabel.SPAM, Instant.now());

    assertNull(example.getBodyText());
    assertNull(example.getBodySource());
  }
}
