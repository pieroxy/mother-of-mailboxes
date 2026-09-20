package net.pieroxy.imf.detection.reputation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Parses the raw content of a downloaded list (one entry per line, blank lines ignored) into a
 * usable {@link ReputationList}. "#" or ";" start a comment, either at the start of a line or
 * trailing after an entry — e.g. Spamhaus DROP (https://www.spamhaus.org/drop/drop.txt) publishes
 * {@code 1.10.16.0/20 ; SBL256894}, where {@code ; SBL256894} must be ignored without
 * invalidating the whole line. An invalid entry is skipped (with a warning) rather than failing
 * the whole list load over a single malformed line — an external list can change format without
 * notice.
 */
public final class ReputationListParser {
  private static final Logger LOGGER = Logger.getLogger(ReputationListParser.class.getName());

  private ReputationListParser() {}

  /** validCount/invalidCount count lines (not final entries: a duplicate domain counts twice on the line side, once in the loaded list) — see {@link ReputationRegistry} for the stats/timing log that uses it. */
  record ParseResult(ReputationList list, int validCount, int invalidCount) {}

  static ParseResult parse(String id, ReputationListType type, String content) {
    return switch (type) {
      case IP_CIDR -> parseIpCidr(id, content);
      case DOMAIN -> parseDomain(content);
    };
  }

  private static ParseResult parseIpCidr(String id, String content) {
    List<CidrRange> ranges = new ArrayList<>();
    int valid = 0;
    int invalid = 0;
    for (String line : lines(content)) {
      try {
        ranges.add(CidrRange.parse(line));
        valid++;
      } catch (IllegalArgumentException e) {
        invalid++;
        LOGGER.warning("Reputation list [" + id + "]: skipping invalid IPv4/CIDR entry \"" + line + "\"");
      }
    }
    return new ParseResult(new IpReputationList(ranges), valid, invalid);
  }

  private static ParseResult parseDomain(String content) {
    Set<String> domains = new HashSet<>();
    int valid = 0;
    for (String line : lines(content)) {
      domains.add(line.toLowerCase(Locale.ROOT));
      valid++;
    }
    return new ParseResult(new DomainReputationList(domains), valid, 0);
  }

  private static List<String> lines(String content) {
    List<String> result = new ArrayList<>();
    for (String raw : content.split("\\r?\\n")) {
      String line = stripComment(raw).trim();
      if (line.isEmpty()) continue;
      result.add(line);
    }
    return result;
  }

  private static String stripComment(String line) {
    int hash = line.indexOf('#');
    int semi = line.indexOf(';');
    int cut = hash < 0 ? semi : (semi < 0 ? hash : Math.min(hash, semi));
    return cut < 0 ? line : line.substring(0, cut);
  }
}
