package net.pieroxy.mom.api.implementations.stats;

import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.rules.MailAccount;
import net.pieroxy.mom.utils.logging.StatsReader;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** One account's daily processing stats over a date range — powers its dedicated stats page. */
@Endpoint(method = ApiMethod.GET)
public class StatsApi extends AbstractAuthenticatedEndpoint<StatsApiInput, StatsApiOutput> {
  // Generous for any real use (over a year), tight enough that a malformed request can't trigger
  // scanning thousands of day files.
  private final static int MAX_RANGE_DAYS = 400;
  private final static int TOP_MATCHERS_LIMIT = 8;
  // Strips every "(...)" detail segment a leaf matcher's debug string carries (see
  // Matcher#matched) — e.g. "FromAddressMatcher(user@spam.example.com)" becomes
  // "FromAddressMatcher", and "IpReputationMatcher[blocklist](score=0.87)" becomes
  // "IpReputationMatcher[blocklist]" (the "[tag]" survives: it names *which* list, a genuinely
  // different matcher, not a per-message value like an address or a score).
  private final static Pattern DETAIL_SEGMENT = Pattern.compile("\\([^()]*\\)");

  public StatsApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public StatsApiOutput processAuthenticated(StatsApiInput input) {
    MailAccount account = serviceProvider.getAccountService().getAccounts().stream()
        .filter(a -> a.getAccountLabel().equals(input.getAccountName()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No such account: " + input.getAccountName()));

    LocalDate from = LocalDate.parse(input.getFrom());
    LocalDate to = LocalDate.parse(input.getTo());
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("\"to\" must not be before \"from\"");
    }
    if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
      throw new IllegalArgumentException("Range too large: max " + MAX_RANGE_DAYS + " days");
    }

    StatsReader.StatsRange range = StatsReader.read(account.getStatsDir(), from, to);

    List<DailyStatsDto> days = range.days().stream()
        .map(d -> new DailyStatsDto(d.date().toString(), d.messagesProcessed(), d.messagesMatched(), d.avgProcessingMs()))
        .collect(Collectors.toList());

    List<MatcherCountDto> topMatchers = topN(range.matcherCounts());
    List<MatcherCountDto> topBlockingMatchers = topN(range.blockingMatcherCounts());
    List<MatcherCountDto> topMatchersByType = topN(byType(range.matcherCounts()));
    List<MatcherCountDto> topBlockingMatchersByType = topN(byType(range.blockingMatcherCounts()));

    return new StatsApiOutput(days, topMatchers, topBlockingMatchers, topMatchersByType, topBlockingMatchersByType);
  }

  private static List<MatcherCountDto> topN(Map<String, Long> counts) {
    return counts.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .limit(TOP_MATCHERS_LIMIT)
        .map(e -> new MatcherCountDto(e.getKey(), e.getValue()))
        .collect(Collectors.toList());
  }

  /**
   * Re-aggregates {@code counts} by matcher type alone, merging every distinct-by-detail entry
   * (e.g. one {@code FromAddressMatcher} per sender address it ever matched) back into one. Must
   * run on the *full* counts map before {@link #topN}, not after: truncating to the top few
   * detailed entries first and only then stripping their names would miss however many other
   * addresses/domains/etc. of the same type fired but individually didn't rank. Package-visible
   * for testing.
   */
  static Map<String, Long> byType(Map<String, Long> counts) {
    Map<String, Long> merged = new LinkedHashMap<>();
    for (Map.Entry<String, Long> e : counts.entrySet()) {
      merged.merge(DETAIL_SEGMENT.matcher(e.getKey()).replaceAll(""), e.getValue(), Long::sum);
    }
    return merged;
  }
}

@TypeScriptType
class StatsApiInput extends AuthenticatedApiInput {
  private String accountName;
  /** ISO-8601 (yyyy-MM-dd), inclusive. */
  private String from;
  /** ISO-8601 (yyyy-MM-dd), inclusive. */
  private String to;

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }

  public String getFrom() {
    return from;
  }

  public void setFrom(String from) {
    this.from = from;
  }

  public String getTo() {
    return to;
  }

  public void setTo(String to) {
    this.to = to;
  }
}

@TypeScriptType
class StatsApiOutput {
  private List<DailyStatsDto> days;
  /** Every matcher that fired at least once in the range, most-fired first, capped at {@link StatsApi#TOP_MATCHERS_LIMIT}. */
  private List<MatcherCountDto> topMatchers;
  /** Of those, only the ones that actually ended processing for a message at least once (see {@code StatsReader#StatsRange}). */
  private List<MatcherCountDto> topBlockingMatchers;
  /** Same population as {@link #topMatchers}, but merged by matcher type — see {@code StatsApi#byType}. */
  private List<MatcherCountDto> topMatchersByType;
  /** Same population as {@link #topBlockingMatchers}, merged by matcher type. */
  private List<MatcherCountDto> topBlockingMatchersByType;

  public StatsApiOutput() {
  }

  public StatsApiOutput(List<DailyStatsDto> days, List<MatcherCountDto> topMatchers, List<MatcherCountDto> topBlockingMatchers,
                         List<MatcherCountDto> topMatchersByType, List<MatcherCountDto> topBlockingMatchersByType) {
    this.days = days;
    this.topMatchers = topMatchers;
    this.topBlockingMatchers = topBlockingMatchers;
    this.topMatchersByType = topMatchersByType;
    this.topBlockingMatchersByType = topBlockingMatchersByType;
  }

  public List<DailyStatsDto> getDays() {
    return days;
  }

  public void setDays(List<DailyStatsDto> days) {
    this.days = days;
  }

  public List<MatcherCountDto> getTopMatchers() {
    return topMatchers;
  }

  public void setTopMatchers(List<MatcherCountDto> topMatchers) {
    this.topMatchers = topMatchers;
  }

  public List<MatcherCountDto> getTopBlockingMatchers() {
    return topBlockingMatchers;
  }

  public void setTopBlockingMatchers(List<MatcherCountDto> topBlockingMatchers) {
    this.topBlockingMatchers = topBlockingMatchers;
  }

  public List<MatcherCountDto> getTopMatchersByType() {
    return topMatchersByType;
  }

  public void setTopMatchersByType(List<MatcherCountDto> topMatchersByType) {
    this.topMatchersByType = topMatchersByType;
  }

  public List<MatcherCountDto> getTopBlockingMatchersByType() {
    return topBlockingMatchersByType;
  }

  public void setTopBlockingMatchersByType(List<MatcherCountDto> topBlockingMatchersByType) {
    this.topBlockingMatchersByType = topBlockingMatchersByType;
  }
}

@TypeScriptType
class DailyStatsDto {
  /** ISO-8601 (yyyy-MM-dd). */
  private String date;
  private long messagesProcessed;
  private long messagesMatched;
  private double avgProcessingMs;

  public DailyStatsDto() {
  }

  public DailyStatsDto(String date, long messagesProcessed, long messagesMatched, double avgProcessingMs) {
    this.date = date;
    this.messagesProcessed = messagesProcessed;
    this.messagesMatched = messagesMatched;
    this.avgProcessingMs = avgProcessingMs;
  }

  public String getDate() {
    return date;
  }

  public void setDate(String date) {
    this.date = date;
  }

  public long getMessagesProcessed() {
    return messagesProcessed;
  }

  public void setMessagesProcessed(long messagesProcessed) {
    this.messagesProcessed = messagesProcessed;
  }

  public long getMessagesMatched() {
    return messagesMatched;
  }

  public void setMessagesMatched(long messagesMatched) {
    this.messagesMatched = messagesMatched;
  }

  public double getAvgProcessingMs() {
    return avgProcessingMs;
  }

  public void setAvgProcessingMs(double avgProcessingMs) {
    this.avgProcessingMs = avgProcessingMs;
  }
}

@TypeScriptType
class MatcherCountDto {
  private String matcher;
  private long count;

  public MatcherCountDto() {
  }

  public MatcherCountDto(String matcher, long count) {
    this.matcher = matcher;
    this.count = count;
  }

  public String getMatcher() {
    return matcher;
  }

  public void setMatcher(String matcher) {
    this.matcher = matcher;
  }

  public long getCount() {
    return count;
  }

  public void setCount(long count) {
    this.count = count;
  }
}
