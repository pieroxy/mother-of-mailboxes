package net.pieroxy.mom.api.implementations.learnedrules;

import net.pieroxy.mom.services.AccountService;
import net.pieroxy.mom.services.ServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractApiEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.general.MailFilterRuleConfiguration;

import java.util.List;

/**
 * Saves an account's whole learned-rules list (see {@code LearnedRulesStore}) and applies it
 * right away — see {@link AccountService#updateLearnedRules}. Deliberately its own endpoint,
 * separate from {@code UpdateAccountApi}: learned rules aren't part of config.json, and applying
 * an edit here needs no account restart, unlike every other section of the settings page.
 */
@Endpoint(method = ApiMethod.POST)
public class UpdateLearnedRulesApi extends AbstractApiEndpoint<UpdateLearnedRulesApiInput, UpdateLearnedRulesApiOutput> {
  private final ServiceProvider serviceProvider;

  public UpdateLearnedRulesApi(ServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  @Override
  public UpdateLearnedRulesApiOutput process(UpdateLearnedRulesApiInput input) {
    List<MailFilterRuleConfiguration> rules = input.getRules() != null ? input.getRules() : List.of();
    for (MailFilterRuleConfiguration rule : rules) {
      if (rule.getMatcher() == null || rule.getMatcher().getType() == null) {
        throw new IllegalArgumentException("A learned rule needs a matcher type.");
      }
      boolean hasKeys = rule.getMatcher().getKeys() != null && !rule.getMatcher().getKeys().isEmpty();
      boolean hasKey = rule.getMatcher().getKey() != null && !rule.getMatcher().getKey().isBlank();
      if (!hasKeys && !hasKey) {
        throw new IllegalArgumentException("A learned rule's matcher needs at least one key.");
      }
      if (rule.getAction() == null || rule.getAction().getType() == null) {
        throw new IllegalArgumentException("A learned rule needs an action type.");
      }
    }

    serviceProvider.getAccountService().updateLearnedRules(input.getAccountName(), rules);
    return new UpdateLearnedRulesApiOutput();
  }
}

@TypeScriptType
class UpdateLearnedRulesApiInput {
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
class UpdateLearnedRulesApiOutput {
}
