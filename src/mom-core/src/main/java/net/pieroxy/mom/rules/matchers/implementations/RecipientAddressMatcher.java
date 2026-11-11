package net.pieroxy.mom.rules.matchers.implementations;

/** Matches a recipient's address: see {@link RecipientMatcher}. */
public class RecipientAddressMatcher extends RecipientMatcher {
  public RecipientAddressMatcher() {
    super("RECIPIENT_ADDRESS_EQUALS");
  }

  @Override
  protected String valueOf(String address) {
    return domainOf(address) != null ? address : null;
  }
}
