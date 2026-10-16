package net.pieroxy.mom.detection.lookalike;

import org.junit.Test;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LookalikeTextTest {
  private static List<String> found(String text) {
    return LookalikeText.findIn("subject", text).stream().map(f -> f.kind() + " " + f.detail()).toList();
  }

  private static void assertClean(String text) {
    assertEquals(text, List.of(), found(text));
  }

  @Test
  public void flagsAWordMixingLatinWithCyrillicOrGreek() {
    assertEquals(List.of("MIXED_SCRIPT Pаypal (Latin+Cyrillic)"), found("Your Pаypal account is locked"));
    assertEquals(List.of("MIXED_SCRIPT Αmazon (Greek+Latin)"), found("Αmazon order"));
  }

  @Test
  public void allowsTheMixesThatAreLegitimate() {
    assertClean("東京タワーとTokyoの旅");      // Japanese: Han + Katakana + Hiragana + Latin
    assertClean("iPhone手机 特价");            // Chinese with Latin
    assertClean("삼성Galaxy 출시");             // Korean with Latin
    assertClean("Café 2024 – l'été arrive !"); // accents, digits, punctuation belong to every alphabet
    assertClean("Скидки на iPhone-а сегодня"); // a hyphen splits words
  }

  @Test
  public void flagsAWholeCyrillicLookalikeWordAmongLatinWords() {
    assertEquals(List.of("WHOLE_SCRIPT Рау → Pay (Cyrillic)"), found("Рау your invoice now"));
  }

  @Test
  public void leavesAGenuinelyRussianOrGreekTextAlone() {
    assertClean("Сор в доме и Apple");   // "Сор" reads as "Cop", but the subject is Russian
    assertClean("Γειά σου Κοσμε");
    assertClean("Рау");                  // no Latin around it: nothing to impersonate
  }

  @Test
  public void flagsFancyLettersButNotOrdinaryUnicode() {
    assertEquals(List.of("FANCY_LETTERS 𝐅𝐫𝐞𝐞 → Free"), found("𝐅𝐫𝐞𝐞 gift for you"));
    assertEquals(List.of("FANCY_LETTERS ⓕⓡⓔⓔ → free"), found("ⓕⓡⓔⓔ gift"));
    assertClean("ＦＲＥＥ ｓａｌｅ");           // full-width: ordinary in CJK text
    assertClean("Brand™ now 10 m² ① first");
  }

  @Test
  public void readsTheSubjectAndTheDisplayNameOfAMessage() throws Exception {
    String raw = "From: =?UTF-8?Q?P=D0=B0ypal_Support?= <x@evil.example>\r\n"
        + "Subject: =?UTF-8?B?" + java.util.Base64.getEncoder().encodeToString("𝐅𝐫𝐞𝐞 gift".getBytes(StandardCharsets.UTF_8)) + "?=\r\n"
        + "\r\nbody\r\n";
    MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()), new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));

    List<String> findings = LookalikeText.findIn(message).stream().map(f -> f.where() + " " + f.kind()).toList();

    assertEquals(List.of("subject FANCY_LETTERS", "display name MIXED_SCRIPT"), findings);
  }

  @Test
  public void readsARawUtf8Subject() throws Exception {
    String raw = "From: a@example.com\r\nSubject: Your Pаypal account\r\n\r\nbody\r\n";
    MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()), new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));

    assertTrue(LookalikeText.findIn(message).stream().anyMatch(f -> f.kind() == LookalikeText.MIXED_SCRIPT));
  }
}
