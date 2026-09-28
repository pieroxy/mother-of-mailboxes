package net.pieroxy.mom.api.implementations.reputation;

import net.pieroxy.mom.api.metadata.AbstractApiEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
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
 * lists are shared process-wide, so — unlike every other endpoint in {@code api.implementations} —
 * this one takes no {@link net.pieroxy.mom.api.ServiceProvider} and reaches the registry directly
 * via its static holder, exactly like the matchers that consume it at runtime do.
 */
@Endpoint(method = ApiMethod.GET)
public class ReputationListsApi extends AbstractApiEndpoint<ReputationListsApiInput, ReputationListsApiOutput> {
  @Override
  public ReputationListsApiOutput process(ReputationListsApiInput input) {
    ReputationRegistry registry = ReputationRegistryHolder.get();
    List<ReputationListDto> lists = registry.getConfiguredLists().stream()
        .map(config -> new ReputationListDto(config, registry))
        .collect(Collectors.toList());
    return new ReputationListsApiOutput(lists);
  }
}

@TypeScriptType
class ReputationListsApiInput {
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
