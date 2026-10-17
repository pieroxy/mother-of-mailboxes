package net.pieroxy.mom.rules.matchers;

/**
 * Result of a matcher test: the boolean, plus a readable description of what matched
 * (e.g. {@code "FromDomainMatcher(gmail.com)"}) — useful in logs to know exactly why a rule
 * fired, in particular which of possibly several configured "keys" was the one that hit.
 * {@code debugString} is {@code null} when {@code matched} is false: there's nothing to explain
 * about a non-match.
 */
public record MatchResult(boolean matched, String debugString) {
  public static MatchResult matched(String debugString) {
    return new MatchResult(true, debugString);
  }

  public static MatchResult notMatched() {
    return new MatchResult(false, null);
  }
}
