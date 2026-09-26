package net.pieroxy.mom.api.implementations.stats;

import net.pieroxy.mom.api.ServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractApiEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.rules.MailAccount;
import net.pieroxy.mom.utils.logging.StatsReader;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** One account's daily processing stats over a date range — powers its dedicated stats page. */
@Endpoint(method = ApiMethod.GET)
public class StatsApi extends AbstractApiEndpoint<StatsApiInput, StatsApiOutput> {
  // Generous for any real use (over a year), tight enough that a malformed request can't trigger
  // scanning thousands of day files.
  private final static int MAX_RANGE_DAYS = 400;
  private final static int TOP_MATCHERS_LIMIT = 8;

  private final ServiceProvider serviceProvider;

  public StatsApi(ServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  @Override
  public StatsApiOutput process(StatsApiInput input) {
    MailAccount account = serviceProvider.getAccounts().stream()
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

    return new StatsApiOutput(days, topMatchers, topBlockingMatchers);
  }

  private static List<MatcherCountDto> topN(Map<String, Long> counts) {
    return counts.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .limit(TOP_MATCHERS_LIMIT)
        .map(e -> new MatcherCountDto(e.getKey(), e.getValue()))
        .collect(Collectors.toList());
  }
}

@TypeScriptType
class StatsApiInput {
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

  public StatsApiOutput() {
  }

  public StatsApiOutput(List<DailyStatsDto> days, List<MatcherCountDto> topMatchers, List<MatcherCountDto> topBlockingMatchers) {
    this.days = days;
    this.topMatchers = topMatchers;
    this.topBlockingMatchers = topBlockingMatchers;
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
