package net.pieroxy.mom.api.implementations.decisions;

import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.rules.MailAccount;
import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.utils.logging.DecisionLog;
import net.pieroxy.mom.utils.logging.DecisionReader;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** One page of an account's decision log (see {@link DecisionLog}), newest first, filtered server-side. */
@Endpoint(method = ApiMethod.GET)
public class DecisionsApi extends AbstractAuthenticatedEndpoint<DecisionsApiInput, DecisionsApiOutput> {
  private final static int MAX_RANGE_DAYS = 400;
  private final static int DEFAULT_LIMIT = 50;
  private final static int MAX_LIMIT = 200;
  // Bounds the work done by one call when the filters match little: a few MB of JSON at most.
  final static int SCAN_BUDGET = 20_000;

  public DecisionsApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public DecisionsApiOutput processAuthenticated(DecisionsApiInput input) {
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
    int limit = input.getLimit() <= 0 ? DEFAULT_LIMIT : Math.min(input.getLimit(), MAX_LIMIT);

    DecisionReader.Filter filter = new DecisionReader.Filter(toSet(input.getTriggers()),
        toSet(input.getOutcomes()), input.isErrorsOnly(), input.getRule(),
        input.getMatcher() == null || input.getMatcher().isBlank() ? null
            : new DecisionReader.MatcherFilter(input.getMatcher(), input.isMatcherByType(), input.isMatcherBlockingOnly()),
        input.getQuery());
    DecisionReader.Page page = DecisionReader.read(account.getStatsDir(), from, to, filter, input.getCursor(), limit, SCAN_BUDGET);

    int retentionDays = account.getConfig().getDecisionLogRetentionDays();
    return new DecisionsApiOutput(retentionDays, page.entries().stream().map(DecisionsApi::toDto).toList(), page.nextCursor());
  }

  static DecisionDto toDto(DecisionLog.Entry entry) {
    DecisionLog.RuleOutcome blocking = DecisionReader.blockingRuleOf(entry);
    List<RuleOutcomeDto> rules = entry.rules() == null ? null : entry.rules().stream()
        .map(r -> new RuleOutcomeDto(r.rule(), r.learnedRule(), r.matched(), r.matchDetail(), r.actionName(), Boolean.TRUE.equals(r.spam()),
            r.keepProcessing(), r.exception()))
        .toList();
    return new DecisionDto(entry.date(), entry.trigger(), entry.messageId(), entry.subject(), entry.from(), entry.processedMs(),
        DecisionReader.outcomeOf(entry), blocking != null ? blocking.rule() : null, blocking != null ? blocking.actionName() : null,
        blocking != null && Boolean.TRUE.equals(blocking.spam()), DecisionReader.hasError(entry), rules,
        entry.learnedRule(), entry.reasonForMoveToDone());
  }

  private static <E extends Enum<E>> Set<E> toSet(List<E> values) {
    return values == null || values.isEmpty() ? null : EnumSet.copyOf(values);
  }
}

@TypeScriptType
class DecisionsApiInput extends AuthenticatedApiInput {
  private String accountName;
  /** ISO-8601 (yyyy-MM-dd), inclusive. */
  private String from;
  /** ISO-8601 (yyyy-MM-dd), inclusive. */
  private String to;
  /** Null or empty: any. */
  private List<DecisionLog.Trigger> triggers;
  /** Null or empty: any. */
  private List<DecisionReader.Outcome> outcomes;
  /** Only entries where at least one rule threw. */
  private boolean errorsOnly;
  /** Only entries where a rule with exactly this description matched. */
  private String rule;
  /** A key from StatsApi's top matchers: only entries where that matcher matched. */
  private String matcher;
  /** {@code matcher} is a "by matcher type" key. */
  private boolean matcherByType;
  /** Only when {@code matcher} is the one that stopped evaluation. */
  private boolean matcherBlockingOnly;
  /** Case- and accent-insensitive substring of the subject, From or Message-ID. */
  private String query;
  /** A previous page's {@code nextCursor}; null for the first page. */
  private String cursor;
  /** 0: default. */
  private int limit;

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

  public List<DecisionLog.Trigger> getTriggers() {
    return triggers;
  }

  public void setTriggers(List<DecisionLog.Trigger> triggers) {
    this.triggers = triggers;
  }

  public List<DecisionReader.Outcome> getOutcomes() {
    return outcomes;
  }

  public void setOutcomes(List<DecisionReader.Outcome> outcomes) {
    this.outcomes = outcomes;
  }

  public boolean isErrorsOnly() {
    return errorsOnly;
  }

  public void setErrorsOnly(boolean errorsOnly) {
    this.errorsOnly = errorsOnly;
  }

  public String getRule() {
    return rule;
  }

  public void setRule(String rule) {
    this.rule = rule;
  }

  public String getMatcher() {
    return matcher;
  }

  public void setMatcher(String matcher) {
    this.matcher = matcher;
  }

  public boolean isMatcherByType() {
    return matcherByType;
  }

  public void setMatcherByType(boolean matcherByType) {
    this.matcherByType = matcherByType;
  }

  public boolean isMatcherBlockingOnly() {
    return matcherBlockingOnly;
  }

  public void setMatcherBlockingOnly(boolean matcherBlockingOnly) {
    this.matcherBlockingOnly = matcherBlockingOnly;
  }

  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query;
  }

  public String getCursor() {
    return cursor;
  }

  public void setCursor(String cursor) {
    this.cursor = cursor;
  }

  public int getLimit() {
    return limit;
  }

  public void setLimit(int limit) {
    this.limit = limit;
  }
}

@TypeScriptType
class DecisionsApiOutput {
  /** The account's current setting; 0 means logging is off, though older files may still be listed. */
  private int retentionDays;
  private List<DecisionDto> entries;
  /** Null when nothing older is left in the range. May be set on a short (even empty) page: see {@link DecisionReader#read}. */
  private String nextCursor;

  public DecisionsApiOutput() {
  }

  public DecisionsApiOutput(int retentionDays, List<DecisionDto> entries, String nextCursor) {
    this.retentionDays = retentionDays;
    this.entries = entries;
    this.nextCursor = nextCursor;
  }

  public int getRetentionDays() {
    return retentionDays;
  }

  public void setRetentionDays(int retentionDays) {
    this.retentionDays = retentionDays;
  }

  public List<DecisionDto> getEntries() {
    return entries;
  }

  public void setEntries(List<DecisionDto> entries) {
    this.entries = entries;
  }

  public String getNextCursor() {
    return nextCursor;
  }

  public void setNextCursor(String nextCursor) {
    this.nextCursor = nextCursor;
  }
}

/** See {@link DecisionLog.Entry}; {@code outcome}, {@code blocking*} and {@code hasError} are derived from {@code rules}. */
@TypeScriptType
class DecisionDto {
  /** UTC, "yyyy-MM-dd HH:mm:ssZ". */
  private String date;
  private String trigger;
  private String messageId;
  private String subject;
  private String from;
  private Long processedMs;
  private DecisionReader.Outcome outcome;
  private String blockingRule;
  /** Null for an entry logged before actions were named. */
  private String blockingActionName;
  private boolean blockingSpam;
  private boolean hasError;
  /** Null for a LEARNING entry. */
  private List<RuleOutcomeDto> rules;
  private String learnedRule;
  private String reasonForMoveToDone;

  public DecisionDto() {
  }

  public DecisionDto(String date, String trigger, String messageId, String subject, String from, Long processedMs,
                     DecisionReader.Outcome outcome, String blockingRule, String blockingActionName, boolean blockingSpam,
                     boolean hasError, List<RuleOutcomeDto> rules,
                     String learnedRule, String reasonForMoveToDone) {
    this.date = date;
    this.trigger = trigger;
    this.messageId = messageId;
    this.subject = subject;
    this.from = from;
    this.processedMs = processedMs;
    this.outcome = outcome;
    this.blockingRule = blockingRule;
    this.blockingActionName = blockingActionName;
    this.blockingSpam = blockingSpam;
    this.hasError = hasError;
    this.rules = rules;
    this.learnedRule = learnedRule;
    this.reasonForMoveToDone = reasonForMoveToDone;
  }

  public String getDate() {
    return date;
  }

  public void setDate(String date) {
    this.date = date;
  }

  public String getTrigger() {
    return trigger;
  }

  public void setTrigger(String trigger) {
    this.trigger = trigger;
  }

  public String getMessageId() {
    return messageId;
  }

  public void setMessageId(String messageId) {
    this.messageId = messageId;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getFrom() {
    return from;
  }

  public void setFrom(String from) {
    this.from = from;
  }

  public Long getProcessedMs() {
    return processedMs;
  }

  public void setProcessedMs(Long processedMs) {
    this.processedMs = processedMs;
  }

  public DecisionReader.Outcome getOutcome() {
    return outcome;
  }

  public void setOutcome(DecisionReader.Outcome outcome) {
    this.outcome = outcome;
  }

  public String getBlockingRule() {
    return blockingRule;
  }

  public void setBlockingRule(String blockingRule) {
    this.blockingRule = blockingRule;
  }

  public String getBlockingActionName() {
    return blockingActionName;
  }

  public void setBlockingActionName(String blockingActionName) {
    this.blockingActionName = blockingActionName;
  }

  public boolean isBlockingSpam() {
    return blockingSpam;
  }

  public void setBlockingSpam(boolean blockingSpam) {
    this.blockingSpam = blockingSpam;
  }

  public boolean isHasError() {
    return hasError;
  }

  public void setHasError(boolean hasError) {
    this.hasError = hasError;
  }

  public List<RuleOutcomeDto> getRules() {
    return rules;
  }

  public void setRules(List<RuleOutcomeDto> rules) {
    this.rules = rules;
  }

  public String getLearnedRule() {
    return learnedRule;
  }

  public void setLearnedRule(String learnedRule) {
    this.learnedRule = learnedRule;
  }

  public String getReasonForMoveToDone() {
    return reasonForMoveToDone;
  }

  public void setReasonForMoveToDone(String reasonForMoveToDone) {
    this.reasonForMoveToDone = reasonForMoveToDone;
  }
}

/** See {@link DecisionLog.RuleOutcome}. */
@TypeScriptType
class RuleOutcomeDto {
  private String rule;
  private boolean learnedRule;
  private Boolean matched;
  private String matchDetail;
  private String actionName;
  private boolean spam;
  private Boolean keepProcessing;
  private String exception;

  public RuleOutcomeDto() {
  }

  public RuleOutcomeDto(String rule, boolean learnedRule, Boolean matched, String matchDetail, String actionName, boolean spam,
                        Boolean keepProcessing, String exception) {
    this.rule = rule;
    this.learnedRule = learnedRule;
    this.matched = matched;
    this.matchDetail = matchDetail;
    this.actionName = actionName;
    this.spam = spam;
    this.keepProcessing = keepProcessing;
    this.exception = exception;
  }

  public String getRule() {
    return rule;
  }

  public void setRule(String rule) {
    this.rule = rule;
  }

  public boolean isLearnedRule() {
    return learnedRule;
  }

  public void setLearnedRule(boolean learnedRule) {
    this.learnedRule = learnedRule;
  }

  public Boolean getMatched() {
    return matched;
  }

  public void setMatched(Boolean matched) {
    this.matched = matched;
  }

  public String getMatchDetail() {
    return matchDetail;
  }

  public void setMatchDetail(String matchDetail) {
    this.matchDetail = matchDetail;
  }

  public String getActionName() {
    return actionName;
  }

  public void setActionName(String actionName) {
    this.actionName = actionName;
  }

  public boolean isSpam() {
    return spam;
  }

  public void setSpam(boolean spam) {
    this.spam = spam;
  }

  public Boolean getKeepProcessing() {
    return keepProcessing;
  }

  public void setKeepProcessing(Boolean keepProcessing) {
    this.keepProcessing = keepProcessing;
  }

  public String getException() {
    return exception;
  }

  public void setException(String exception) {
    this.exception = exception;
  }
}
