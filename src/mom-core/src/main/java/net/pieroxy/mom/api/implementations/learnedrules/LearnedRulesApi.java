package net.pieroxy.mom.api.implementations.learnedrules;

import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.general.MailFilterRuleConfiguration;
import net.pieroxy.mom.rules.MailAccount;

import java.util.List;

/**
 * One account's rules learned by example (see {@code LearnedRulesStore}) — for the webapp's
 * Learned Rules section, so a bad example (a message dropped in the wrong mom-rules/ folder by
 * mistake) can be corrected by hand instead of hand-editing the JSON file directly.
 */
@Endpoint(method = ApiMethod.GET)
public class LearnedRulesApi extends AbstractAuthenticatedEndpoint<LearnedRulesApiInput, LearnedRulesApiOutput> {
  public LearnedRulesApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public LearnedRulesApiOutput processAuthenticated(LearnedRulesApiInput input) {
    MailAccount account = serviceProvider.getAccountService().getAccounts().stream()
        .filter(a -> a.getAccountLabel().equals(input.getAccountName()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No such account: " + input.getAccountName()));
    return new LearnedRulesApiOutput(account.getLearnedRules());
  }
}

@TypeScriptType
class LearnedRulesApiInput extends AuthenticatedApiInput {
  private String accountName;

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }
}

@TypeScriptType
class LearnedRulesApiOutput {
  private List<MailFilterRuleConfiguration> rules;

  public LearnedRulesApiOutput() {
  }

  public LearnedRulesApiOutput(List<MailFilterRuleConfiguration> rules) {
    this.rules = rules;
  }

  public List<MailFilterRuleConfiguration> getRules() {
    return rules;
  }

  public void setRules(List<MailFilterRuleConfiguration> rules) {
    this.rules = rules;
  }
}
