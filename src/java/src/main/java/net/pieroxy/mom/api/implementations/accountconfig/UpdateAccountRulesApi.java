package net.pieroxy.mom.api.implementations.accountconfig;

import net.pieroxy.mom.api.ServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractApiEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.general.MailFilterRuleConfiguration;

import java.util.List;

/**
 * Saves the account's whole {@code rules} list and restarts it — see
 * {@link ServiceProvider#updateRules}. For now the only editor that calls this is the Rules
 * card's reorder (move up/down) controls on the settings page, so {@code rules} is always the
 * exact list {@code AccountConfigApi} handed out, just reordered — a future rule editor can reuse
 * the same endpoint for actual content changes.
 */
@Endpoint(method = ApiMethod.POST)
public class UpdateAccountRulesApi extends AbstractApiEndpoint<UpdateAccountRulesApiInput, UpdateAccountRulesApiOutput> {
  private final ServiceProvider serviceProvider;

  public UpdateAccountRulesApi(ServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  @Override
  public UpdateAccountRulesApiOutput process(UpdateAccountRulesApiInput input) {
    if (input.getRules() == null) {
      throw new IllegalArgumentException("rules must not be null.");
    }

    serviceProvider.updateRules(input.getAccountName(), input.getRules());

    return new UpdateAccountRulesApiOutput();
  }
}

@TypeScriptType
class UpdateAccountRulesApiInput {
  private String accountName;
  private List<MailFilterRuleConfiguration> rules;

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }

  public List<MailFilterRuleConfiguration> getRules() {
    return rules;
  }

  public void setRules(List<MailFilterRuleConfiguration> rules) {
    this.rules = rules;
  }
}

@TypeScriptType
class UpdateAccountRulesApiOutput {
}
