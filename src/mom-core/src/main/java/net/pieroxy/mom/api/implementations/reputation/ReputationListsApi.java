package net.pieroxy.mom.api.implementations.reputation;

import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.detection.reputation.ReputationRegistry;
import net.pieroxy.mom.detection.reputation.ReputationRegistryHolder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The reputation lists declared in the global config (see {@code Configuration#reputationLists}) —
 * used by the webapp's RuleEditPage (id + type is all it needs, to offer a dropdown when editing
 * an IP_REPUTATION_EQUALS/FROM_DOMAIN_REPUTATION_EQUALS matcher's reputation list ids instead of
 * free text, filtered client-side by type), by GeneralSettingsPage (every config field, to
 * actually manage the list), and by HomePage's dashboard (the live fields too — last refresh,
 * item count, content size — status itself is computed client-side from lastRefreshTimestamp and
 * refreshHours, same as an account's cycle progress bar). Global, not per-account: reputation
 * lists are shared process-wide, so this one reaches the registry directly via its static holder,
 * exactly like the matchers that consume it at runtime do, rather than through
 * {@link net.pieroxy.mom.services.IServiceProvider} like every other endpoint's own business
 * logic — {@code IServiceProvider} is only here for {@link AbstractAuthenticatedEndpoint}'s
 * session check.
 */
@Endpoint(method = ApiMethod.GET)
public class ReputationListsApi extends AbstractAuthenticatedEndpoint<ReputationListsApiInput, ReputationListsApiOutput> {
  public ReputationListsApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public ReputationListsApiOutput processAuthenticated(ReputationListsApiInput input) {
    ReputationRegistry registry = ReputationRegistryHolder.get();
    List<ReputationListDto> lists = registry.getConfiguredLists().stream()
        .map(config -> new ReputationListDto(config, registry))
        .collect(Collectors.toList());
    return new ReputationListsApiOutput(lists);
  }
}

@TypeScriptType
class ReputationListsApiInput extends AuthenticatedApiInput {
}

@TypeScriptType
class ReputationListsApiOutput {
  private List<ReputationListDto> lists;

  public ReputationListsApiOutput() {
  }

  public ReputationListsApiOutput(List<ReputationListDto> lists) {
    this.lists = lists;
  }

  public List<ReputationListDto> getLists() {
    return lists;
  }

  public void setLists(List<ReputationListDto> lists) {
    this.lists = lists;
  }
}
