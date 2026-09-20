package net.pieroxy.imf.detection.reputation;

/**
 * Gives matchers access to the process's {@link ReputationRegistry}. Matchers are built without
 * any context by {@code MatcherType.getImplementation()} (just a no-arg constructor) — there's
 * no other way to hand it to them. Unlike {@code net.pieroxy.imf.rules.RuleContext} (one value
 * per account, bound at rule-build time), a single registry serves the whole process: set once by
 * {@code Runner.main} before the first account starts, read by every thread afterward. Defaults
 * to {@link ReputationRegistry#empty()} so that a matcher built outside this normal startup
 * (typically a test that doesn't care about reputation) doesn't blow up.
 */
public final class ReputationRegistryHolder {
  private static volatile ReputationRegistry instance = ReputationRegistry.empty();

  private ReputationRegistryHolder() {}

  public static void set(ReputationRegistry registry) {
    instance = registry;
  }

  public static ReputationRegistry get() {
    return instance;
  }
}
