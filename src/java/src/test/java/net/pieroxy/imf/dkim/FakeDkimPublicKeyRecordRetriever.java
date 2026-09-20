package net.pieroxy.imf.dkim;

import net.pieroxy.imf.detection.dkim.DkimVerifier;
import org.apache.james.jdkim.api.PublicKeyRecordRetriever;
import org.apache.james.jdkim.exceptions.PermFailException;
import org.apache.james.jdkim.exceptions.TempFailException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** In-memory DKIM public key retriever, for testing {@link DkimVerifier} without a network. */
public class FakeDkimPublicKeyRecordRetriever implements PublicKeyRecordRetriever {
  private final Map<String, List<String>> records = new HashMap<>();

  public FakeDkimPublicKeyRecordRetriever withRecord(String selector, String domain, String record) {
    records.put(key(selector, domain), List.of(record));
    return this;
  }

  @Override
  public List<String> getRecords(CharSequence methodAndOption, CharSequence selector, CharSequence token)
          throws TempFailException, PermFailException {
    return records.getOrDefault(key(selector.toString(), token.toString()), List.of());
  }

  private static String key(String selector, String domain) {
    return selector.toLowerCase() + ":" + domain.toLowerCase();
  }
}
