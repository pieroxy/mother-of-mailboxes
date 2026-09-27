package net.pieroxy.mom.api.implementations.reputation;

import net.pieroxy.mom.api.metadata.AbstractApiEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.detection.reputation.ReputationListType;
import net.pieroxy.mom.detection.reputation.ReputationRegistryHolder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The reputation lists declared in the global config (see {@code Configuration#reputationLists}),
 * id + type only — just enough for the webapp's RuleEditPage to offer a dropdown when editing an
 * IP_REPUTATION_EQUALS/FROM_DOMAIN_REPUTATION_EQUALS matcher's reputation list ids, instead of
 * free text (filtered client-side by type, since a list's type determines which of those two
 * matchers can use it — see {@code ReputationRegistry}). Global, not per-account: reputation lists
 * are shared process-wide, so — unlike every other endpoint in {@code api.implementations} — this
 * one takes no {@link net.pieroxy.mom.api.ServiceProvider} and reaches the registry directly via
 * its static holder, exactly like the matchers that consume it at runtime do.
 */
@Endpoint(method = ApiMethod.GET)
public class ReputationListsApi extends AbstractApiEndpoint<ReputationListsApiInput, ReputationListsApiOutput> {
  @Override
  public ReputationListsApiOutput process(ReputationListsApiInput input) {
    List<ReputationListDto> lists = ReputationRegistryHolder.get().getConfiguredLists().stream()
        .map(cfg -> new ReputationListDto(cfg.getId(), cfg.getType()))
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

@TypeScriptType
class ReputationListDto {
  private String id;
  private ReputationListType type;

  public ReputationListDto() {
  }

  public ReputationListDto(String id, ReputationListType type) {
    this.id = id;
    this.type = type;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public ReputationListType getType() {
    return type;
  }

  public void setType(ReputationListType type) {
    this.type = type;
  }
}
