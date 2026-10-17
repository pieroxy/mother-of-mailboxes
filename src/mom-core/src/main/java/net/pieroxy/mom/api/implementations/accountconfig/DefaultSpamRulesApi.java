package net.pieroxy.mom.api.implementations.accountconfig;

import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.general.MailFilterRuleConfiguration;
import net.pieroxy.mom.rules.DefaultSpamRules;
import net.pieroxy.mom.services.IServiceProvider;

import java.util.List;

/** Previews the rules {@link CreateAccountApi} generates when spam handling is enabled — see {@link DefaultSpamRules}. */
@Endpoint(method = ApiMethod.GET)
public class DefaultSpamRulesApi extends AbstractAuthenticatedEndpoint<DefaultSpamRulesApiInput, DefaultSpamRulesApiOutput> {
  public DefaultSpamRulesApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public DefaultSpamRulesApiOutput processAuthenticated(DefaultSpamRulesApiInput input) {
    return new DefaultSpamRulesApiOutput(DefaultSpamRules.build(
        CreateAccountApi.spamFolderOrDefault(input.getSpamFolder()), input.getClassifierFolder(),
        CreateAccountApi.configuredListIds(serviceProvider)));
  }
}

@TypeScriptType
class DefaultSpamRulesApiInput extends AuthenticatedApiInput {
  private String spamFolder;
  private String classifierFolder;

  public String getSpamFolder() {
    return spamFolder;
  }

  public void setSpamFolder(String spamFolder) {
    this.spamFolder = spamFolder;
  }

  public String getClassifierFolder() {
    return classifierFolder;
  }

  public void setClassifierFolder(String classifierFolder) {
    this.classifierFolder = classifierFolder;
  }
}

@TypeScriptType
class DefaultSpamRulesApiOutput {
  private List<MailFilterRuleConfiguration> rules;

  public DefaultSpamRulesApiOutput() {
  }

  public DefaultSpamRulesApiOutput(List<MailFilterRuleConfiguration> rules) {
    this.rules = rules;
  }

  public List<MailFilterRuleConfiguration> getRules() {
    return rules;
  }

  public void setRules(List<MailFilterRuleConfiguration> rules) {
    this.rules = rules;
  }
}
