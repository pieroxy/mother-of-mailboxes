package net.pieroxy.mom.rules.matchers.implementations;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a config key like {@code ">0.5"}/{@code "<=0.2"} and tests a score against it — same
 * format as {@link SubjectClassifierMatcher}, shared by {@link IpReputationMatcher} and
 * {@link FromDomainReputationMatcher}.
 */
final class ReputationThreshold {
  private static final Pattern PATTERN = Pattern.compile("(>=|<=|>|<)\\s*([0-9]*\\.?[0-9]+)");

  private final String operator;
  private final double value;

  private ReputationThreshold(String operator, double value) {
    this.operator = operator;
    this.value = value;
  }

  static ReputationThreshold parse(String key, String matcherTypeName) {
    Matcher m = PATTERN.matcher(key == null ? "" : key.trim());
    if (!m.matches()) {
      throw new IllegalArgumentException(matcherTypeName + " key must look like \">0.5\" or \"<=0.2\", got: " + key);
    }
    return new ReputationThreshold(m.group(1), Double.parseDouble(m.group(2)));
  }

  boolean test(double score) {
    return switch (operator) {
      case ">" -> score > value;
      case ">=" -> score >= value;
      case "<" -> score < value;
      case "<=" -> score <= value;
      default -> throw new IllegalStateException("Unknown operator: " + operator);
    };
  }

  @Override
  public String toString() {
    return operator + value;
  }
}
