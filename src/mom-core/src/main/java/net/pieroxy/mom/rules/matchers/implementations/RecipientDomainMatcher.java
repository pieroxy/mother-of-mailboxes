package net.pieroxy.mom.rules.matchers.implementations;

/** Matches a recipient's domain: see {@link RecipientMatcher}. */
public class RecipientDomainMatcher extends RecipientMatcher {
  public RecipientDomainMatcher() {
    super("RECIPIENT_DOMAIN_EQUALS");
  }

  @Override
  protected String valueOf(String address) {
    return domainOf(address);
  }
}
