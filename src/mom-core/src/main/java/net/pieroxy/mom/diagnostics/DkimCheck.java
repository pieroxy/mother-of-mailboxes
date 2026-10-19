package net.pieroxy.mom.diagnostics;

import net.pieroxy.mom.detection.dmarc.DmarcEvaluator;
import org.apache.james.jdkim.DKIMVerifier;
import org.apache.james.jdkim.api.PublicKeyRecordRetriever;
import org.apache.james.jdkim.api.Result;
import org.apache.james.jdkim.api.SignatureRecord;
import org.apache.james.jdkim.exceptions.FailException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Checks the DKIM signature(s) (RFC 6376) of the test message (see {@link TestSends}): whether
 * it's signed at all, whether each signature verifies, how strong it is (algorithm, key size, no
 * body length limit, key not in testing mode), and whether one is by the From domain — the only
 * kind that counts toward its DMARC. Nothing to check without a test message: there's no way to
 * list a domain's DKIM keys from the DNS, nor to know which one its mail is signed with.
 */
public class DkimCheck {
  private static final Pattern DKIM_SIGNATURE = Pattern.compile("(?im)^DKIM-Signature:");

  private final PublicKeyRecordRetriever retriever;

  public DkimCheck(PublicKeyRecordRetriever retriever) {
    this.retriever = retriever;
  }

  public DeliveryCheckResultDto run(String fromDomain, Supplier<TestSendOutcome> testSend) {
    TestSendOutcome outcome = testSend.get();
    switch (outcome.kind()) {
      case PENDING:
        return DeliveryCheckResultDto.inProgress(DeliveryCheck.DKIM, outcome.message());
      case NOT_CONFIGURED:
        return DeliveryCheckResultDto.skipped(DeliveryCheck.DKIM, "Needs an SMTP sink: DKIM can only be checked on a message "
            + "actually sent, and none can be without one. Set it in General settings.");
      case FAILED:
        return DeliveryCheckResultDto.skipped(DeliveryCheck.DKIM, "Needs the test message, but the test send failed: " + outcome.message());
      default:
        return check(fromDomain, outcome.received().raw());
    }
  }

  private DeliveryCheckResultDto check(String fromDomain, byte[] raw) {
    DeliveryCheckResultDto result = new DeliveryCheckResultDto(DeliveryCheck.DKIM).fact("From domain", fromDomain);
    RecordingRetriever recording = new RecordingRetriever(retriever);
    DKIMVerifier verifier = new DKIMVerifier(recording);
    String failure = null;
    try {
      verifier.verify(new ByteArrayInputStream(raw));
    } catch (FailException e) {
      failure = e.getMessage(); // The per-signature detail is still in getResults().
    } catch (IOException e) {
      return result.finding(CheckStatus.WARNING, "Could not read the test message: " + e.getMessage()).conclude("");
    }

    List<Result> results = verifier.getResults() == null ? List.of() : verifier.getResults();
    if (results.isEmpty()) {
      if (DKIM_SIGNATURE.matcher(headersOf(raw)).find()) {
        return result.finding(CheckStatus.ERROR, "The test message has a DKIM signature that couldn't be read"
            + (failure != null ? " (" + failure + ")" : "") + ".").conclude("");
      }
      return result.finding(CheckStatus.ERROR, "The test message isn't DKIM-signed: receiving servers can't verify it really "
          + "comes from " + fromDomain + ", and DMARC then relies on SPF alone. Turn on DKIM signing for " + fromDomain
          + " in your mail server or provider, and publish the key it gives you in the DNS.").conclude("");
    }

    boolean alignedValid = false;
    for (Result r : results) {
      SignatureRecord sig = r.getRecord();
      String domain = sig.getDToken().toString().toLowerCase(Locale.ROOT);
      String selector = sig.getSelector().toString();
      String algorithm = sig.getHashKeyType() + "-" + sig.getHashMethod();
      String name = domain + " (selector " + selector + ")";
      result.fact("Signature", "d=" + domain + " s=" + selector + " a=" + algorithm
          + " c=" + sig.getHeaderCanonicalisationMethod() + "/" + sig.getBodyCanonicalisationMethod());

      String keyName = selector.toLowerCase(Locale.ROOT) + "._domainkey." + domain;
      String keyRecord = recording.records.get(keyName);
      if (keyRecord == null && !r.isSuccess() && r.getResultType() != Result.Type.TEMPERROR) {
        // jdkim reports a missing key as a plain "fail": say what's actually wrong.
        result.finding(CheckStatus.ERROR, "The signature by " + name + " can't be verified: there's no DKIM key published at "
            + keyName + ". Publish the key your mail server or provider gives you there.");
      } else switch (r.getResultType()) {
        case PASS -> result.finding(CheckStatus.OK, "The signature by " + name + " is valid.");
        case FAIL -> result.finding(CheckStatus.ERROR, "The signature by " + name + " doesn't verify (" + r.getErrorMessage()
            + "): the key published in the DNS doesn't match the one signing your mail, or something modified the message "
            + "after it was signed.");
        case TEMPERROR -> result.finding(CheckStatus.WARNING, "Could not fetch the key of the signature by " + name + " ("
            + r.getErrorMessage() + "). Try again later.");
        default -> result.finding(CheckStatus.ERROR, "The signature by " + name + " can't be verified (" + r.getErrorMessage()
            + "). Check that the key is published at " + selector + "._domainkey." + domain + ".");
      }

      if ("sha1".equalsIgnoreCase(sig.getHashMethod().toString())) {
        result.finding(CheckStatus.WARNING, "The signature by " + name + " uses " + algorithm + ": SHA-1 is obsolete (RFC 8301) "
            + "and many receivers ignore such signatures. Switch to rsa-sha256.");
      }
      if (sig.getBodyHashLimit() >= 0) {
        result.finding(CheckStatus.WARNING, "The signature by " + name + " only covers the first " + sig.getBodyHashLimit()
            + " bytes of the body (l= tag): anyone can append content to your messages without breaking it. Remove l=.");
      }
      addKeyFindings(result, name, keyRecord);

      boolean aligned = organizationalDomain(domain).equals(organizationalDomain(fromDomain));
      if (r.isSuccess() && aligned) alignedValid = true;
      if (!aligned) {
        result.finding(r.isSuccess() ? CheckStatus.INFO : CheckStatus.WARNING, "The signature by " + name + " isn't by "
            + fromDomain + " (nor a domain under the same organization): it doesn't count toward " + fromDomain + "'s DMARC.");
      }
    }

    if (results.stream().anyMatch(Result::isSuccess) && !alignedValid) {
      result.finding(CheckStatus.WARNING, "No valid signature by " + fromDomain + ": your mail is signed, but by another domain "
          + "(usually your provider's), which doesn't count toward " + fromDomain + "'s DMARC. Set up DKIM signing for "
          + fromDomain + " itself, if your provider allows it.");
    }
    return result.conclude("Your mail is DKIM-signed by " + fromDomain + ", with a valid signature.");
  }

  /** Key strength and flags, from the DNS record the verification fetched. */
  private static void addKeyFindings(DeliveryCheckResultDto result, String name, String record) {
    if (record == null) return;
    Map<String, String> tags = tags(record);
    String p = tags.getOrDefault("p", "");
    if (p.isEmpty()) {
      result.finding(CheckStatus.ERROR, "The key of " + name + " is revoked (empty p= in its DNS record).");
      return;
    }
    String type = tags.getOrDefault("k", "rsa").toLowerCase(Locale.ROOT);
    if (type.equals("rsa")) {
      try {
        int bits = ((RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getMimeDecoder().decode(p))))
            .getModulus().bitLength();
        result.fact("Key of " + name, "RSA, " + bits + " bits");
        if (bits < 1024) {
          result.finding(CheckStatus.ERROR, "The key of " + name + " is only " + bits + " bits: receivers ignore keys under "
              + "1024 bits (RFC 8301). Generate a 2048-bit key.");
        } else if (bits < 2048) {
          result.finding(CheckStatus.WARNING, "The key of " + name + " is " + bits + " bits: still accepted, but weak by today's "
              + "standards. Move to a 2048-bit key.");
        } else {
          result.finding(CheckStatus.OK, "The key of " + name + " is " + bits + " bits.");
        }
      } catch (Exception e) {
        // Unreadable key: the verification itself already failed and said so.
      }
    } else {
      result.fact("Key of " + name, type);
    }
    if (tags.getOrDefault("t", "").toLowerCase(Locale.ROOT).matches("(.*:)?y(:.*)?")) {
      result.finding(CheckStatus.WARNING, "The key of " + name + " is in testing mode (t=y in its DNS record): receivers may "
          + "treat your mail as if it weren't signed. Remove t=y once signing works.");
    }
  }

  private static Map<String, String> tags(String record) {
    Map<String, String> tags = new HashMap<>();
    for (String part : record.split(";")) {
      int eq = part.indexOf('=');
      if (eq > 0) tags.put(part.substring(0, eq).trim().toLowerCase(Locale.ROOT), part.substring(eq + 1).replaceAll("\\s+", ""));
    }
    return tags;
  }

  private static String headersOf(byte[] raw) {
    String text = new String(raw, StandardCharsets.ISO_8859_1);
    int end = text.indexOf("\r\n\r\n");
    return end < 0 ? text : text.substring(0, end);
  }

  private static String organizationalDomain(String domain) {
    return DmarcEvaluator.organizationalDomainOf(domain).orElse(domain.toLowerCase(Locale.ROOT));
  }

  /** Remembers the key records the verification fetches, for {@link #addKeyFindings}, keyed by "selector._domainkey.domain". */
  private static final class RecordingRetriever implements PublicKeyRecordRetriever {
    private final PublicKeyRecordRetriever delegate;
    final Map<String, String> records = new HashMap<>();

    RecordingRetriever(PublicKeyRecordRetriever delegate) {
      this.delegate = delegate;
    }

    @Override
    public List<String> getRecords(CharSequence methodAndOption, CharSequence selector, CharSequence token)
        throws org.apache.james.jdkim.exceptions.TempFailException, org.apache.james.jdkim.exceptions.PermFailException {
      List<String> found = delegate.getRecords(methodAndOption, selector, token);
      if (found != null && found.size() == 1) {
        records.put(selector.toString().toLowerCase(Locale.ROOT) + "._domainkey." + token.toString().toLowerCase(Locale.ROOT), found.get(0));
      }
      return found == null ? new ArrayList<>() : found;
    }
  }
}
