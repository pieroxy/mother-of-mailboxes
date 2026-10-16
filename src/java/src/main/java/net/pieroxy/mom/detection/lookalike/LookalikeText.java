package net.pieroxy.mom.detection.lookalike;

import net.pieroxy.mom.api.metadata.TypeScriptNonConstEnum;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.detection.from.FromValidity;
import net.pieroxy.mom.utils.MailTools;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeUtility;
import java.io.UnsupportedEncodingException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.Character.UnicodeScript.BOPOMOFO;
import static java.lang.Character.UnicodeScript.CYRILLIC;
import static java.lang.Character.UnicodeScript.GREEK;
import static java.lang.Character.UnicodeScript.HAN;
import static java.lang.Character.UnicodeScript.HANGUL;
import static java.lang.Character.UnicodeScript.HIRAGANA;
import static java.lang.Character.UnicodeScript.KATAKANA;
import static java.lang.Character.UnicodeScript.LATIN;

/**
 * Words written to look like something they aren't, in a message's subject or sender display name
 * — the tricks of UTS #39 (Unicode Security Mechanisms). {@code @TypeScriptNonConstEnum} so the
 * LOOKALIKE_TEXT_EQUALS value picker can list these via {@code Object.values(LookalikeText)}.
 * <p>
 * A word is a run of letters, digits, combining marks and fancy letters: spaces, punctuation,
 * hyphens and apostrophes split words, so {@code iPhone-а} is two.
 */
@TypeScriptType
@TypeScriptNonConstEnum
public enum LookalikeText {
  /**
   * A word mixing alphabets beyond what UTS #39's "moderately restrictive" level allows, e.g.
   * {@code Pаypal} with a Cyrillic а. Allowed: one alphabet, the East Asian mixes (Latin with Han
   * and Hiragana/Katakana, Bopomofo or Hangul), or Latin with one other alphabet but Cyrillic or
   * Greek — the two with letters identical to Latin ones. Digits, punctuation and accents belong to
   * every alphabet.
   */
  MIXED_SCRIPT,
  /**
   * A word entirely in Cyrillic or Greek letters that each look like a Latin one, e.g. {@code Рау}
   * for "Pay", among Latin words. Only when no other word of the text is genuinely Cyrillic or
   * Greek: a Russian or Greek subject has such words naturally.
   */
  WHOLE_SCRIPT,
  /**
   * A word with two or more "fancy" letters — mathematical bold/italic/script (𝐅𝐫𝐞𝐞) or circled
   * (ⓕⓡⓔⓔ) forms of plain Latin letters, used to dodge word filters. Full-width letters don't
   * count: they're ordinary in Japanese and Chinese text.
   */
  FANCY_LETTERS;

  /**
   * @param where  "subject" or "display name".
   * @param detail the word and what's wrong with it, e.g. {@code Pаypal (Latin+Cyrillic)}.
   */
  public record Finding(LookalikeText kind, String where, String word, String detail) {}

  public String getCode() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** Everything found in the subject, then in the sender's display name. A part that can't be read is skipped. */
  public static List<Finding> findIn(Message message) throws MessagingException {
    List<Finding> findings = new ArrayList<>(findIn("subject", subjectOf(message)));
    findings.addAll(findIn("display name", displayNameOf(message)));
    return findings;
  }

  static List<Finding> findIn(String where, String text) {
    List<Finding> findings = new ArrayList<>();
    if (text == null || text.isBlank()) return findings;
    List<String> words = words(text);
    boolean latinContext = false;
    boolean genuineCyrillicOrGreek = false;
    for (String word : words) {
      Set<Character.UnicodeScript> scripts = scriptsOf(word);
      if (scripts.equals(Set.of(LATIN))) latinContext = true;
      if (scripts.size() == 1 && (scripts.contains(CYRILLIC) || scripts.contains(GREEK)) && hasNonLookalikeLetter(word)) {
        genuineCyrillicOrGreek = true;
      }
    }
    for (String word : words) {
      Set<Character.UnicodeScript> scripts = scriptsOf(word);
      if (isMixed(scripts)) {
        findings.add(new Finding(MIXED_SCRIPT, where, word, word + " (" + describe(scripts) + ")"));
      }
      if (latinContext && !genuineCyrillicOrGreek && scripts.size() == 1 && (scripts.contains(CYRILLIC) || scripts.contains(GREEK))) {
        String latin = latinSpelling(word);
        if (latin != null) findings.add(new Finding(WHOLE_SCRIPT, where, word, word + " → " + latin + " (" + describe(scripts) + ")"));
      }
      if (fancyLetters(word) >= 2) {
        findings.add(new Finding(FANCY_LETTERS, where, word, word + " → " + Normalizer.normalize(word, Normalizer.Form.NFKC)));
      }
    }
    return findings;
  }

  private static String subjectOf(Message message) throws MessagingException {
    String[] headers = message.getHeader("Subject");
    if (headers == null || headers.length == 0 || headers[0] == null) return null;
    try {
      return MimeUtility.decodeText(MimeUtility.unfold(MailTools.redecodeUtf8(headers[0])));
    } catch (UnsupportedEncodingException e) {
      return headers[0];
    }
  }

  private static String displayNameOf(Message message) throws MessagingException {
    String[] headers = message.getHeader("From");
    if (headers == null || headers.length == 0 || headers[0] == null) return null;
    try {
      List<InternetAddress> mailboxes = FromValidity.mailboxes(headers[0]);
      return mailboxes.isEmpty() ? null : mailboxes.get(0).getPersonal();
    } catch (AddressException e) {
      return null;
    }
  }

  private static List<String> words(String text) {
    List<String> words = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    text.codePoints().forEach(cp -> {
      if (Character.isLetterOrDigit(cp) || isMark(cp) || isFancy(cp)) {
        current.appendCodePoint(cp);
      } else if (!current.isEmpty()) {
        words.add(current.toString());
        current.setLength(0);
      }
    });
    if (!current.isEmpty()) words.add(current.toString());
    return words;
  }

  /** The word's alphabets; digits, punctuation, accents and the like belong to all of them, so they're left out. */
  private static Set<Character.UnicodeScript> scriptsOf(String word) {
    Set<Character.UnicodeScript> scripts = new LinkedHashSet<>();
    word.codePoints().forEach(cp -> {
      Character.UnicodeScript script = Character.UnicodeScript.of(cp);
      if (script != Character.UnicodeScript.COMMON && script != Character.UnicodeScript.INHERITED
          && script != Character.UnicodeScript.UNKNOWN) {
        scripts.add(script);
      }
    });
    return scripts;
  }

  private static boolean isMixed(Set<Character.UnicodeScript> scripts) {
    if (scripts.size() < 2) return false;
    if (EnumSet.of(LATIN, HAN, HIRAGANA, KATAKANA).containsAll(scripts)) return false;
    if (EnumSet.of(LATIN, HAN, BOPOMOFO).containsAll(scripts)) return false;
    if (EnumSet.of(LATIN, HAN, HANGUL).containsAll(scripts)) return false;
    return !(scripts.size() == 2 && scripts.contains(LATIN) && !scripts.contains(CYRILLIC) && !scripts.contains(GREEK));
  }

  /** The word with each letter replaced by the Latin one it looks like; null if some letter doesn't look like one, or there are fewer than two. */
  private static String latinSpelling(String word) {
    StringBuilder latin = new StringBuilder();
    int letters = 0;
    for (int cp : word.codePoints().toArray()) {
      if (Character.isLetter(cp)) {
        Character lookalike = LatinLookalikes.latinOf(cp);
        if (lookalike == null) return null;
        latin.append(lookalike);
        letters++;
      } else {
        latin.appendCodePoint(cp);
      }
    }
    return letters >= 2 ? latin.toString() : null;
  }

  private static boolean hasNonLookalikeLetter(String word) {
    return word.codePoints().anyMatch(cp -> Character.isLetter(cp) && LatinLookalikes.latinOf(cp) == null);
  }

  private static long fancyLetters(String word) {
    return word.codePoints().filter(LookalikeText::isFancy).count();
  }

  /** A mathematical or enclosed form of a plain Latin letter (e.g. 𝐅, 𝔉, Ⓕ, ⓕ). */
  private static boolean isFancy(int codePoint) {
    Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
    if (block != Character.UnicodeBlock.MATHEMATICAL_ALPHANUMERIC_SYMBOLS && block != Character.UnicodeBlock.ENCLOSED_ALPHANUMERICS
        && block != Character.UnicodeBlock.ENCLOSED_ALPHANUMERIC_SUPPLEMENT) {
      return false;
    }
    String plain = Normalizer.normalize(new String(Character.toChars(codePoint)), Normalizer.Form.NFKC);
    return plain.length() == 1 && ((plain.charAt(0) >= 'a' && plain.charAt(0) <= 'z') || (plain.charAt(0) >= 'A' && plain.charAt(0) <= 'Z'));
  }

  private static boolean isMark(int codePoint) {
    int type = Character.getType(codePoint);
    return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK || type == Character.ENCLOSING_MARK;
  }

  private static String describe(Set<Character.UnicodeScript> scripts) {
    return scripts.stream()
        .map(s -> s.name().charAt(0) + s.name().substring(1).toLowerCase(Locale.ROOT))
        .collect(Collectors.joining("+"));
  }
}
