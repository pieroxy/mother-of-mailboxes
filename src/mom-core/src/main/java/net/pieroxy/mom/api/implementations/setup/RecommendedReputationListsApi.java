package net.pieroxy.mom.api.implementations.setup;

import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.detection.reputation.RecommendedReputationLists;
import net.pieroxy.mom.detection.reputation.ReputationListType;
import net.pieroxy.mom.services.IServiceProvider;

import java.util.List;
import java.util.stream.Collectors;

/** The reputation lists the setup wizard offers — see {@link RecommendedReputationLists}. */
@Endpoint(method = ApiMethod.GET)
public class RecommendedReputationListsApi extends AbstractAuthenticatedEndpoint<RecommendedReputationListsApiInput, RecommendedReputationListsApiOutput> {
  public RecommendedReputationListsApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public RecommendedReputationListsApiOutput processAuthenticated(RecommendedReputationListsApiInput input) {
    return new RecommendedReputationListsApiOutput(RecommendedReputationLists.ALL.stream()
        .map(r -> new RecommendedReputationListDto(r.config().getId(), r.config().getType(), r.config().getUrl(), r.description(), r.approxEntries()))
        .collect(Collectors.toList()));
  }
}

@TypeScriptType
class RecommendedReputationListsApiInput extends AuthenticatedApiInput {
}

@TypeScriptType
class RecommendedReputationListsApiOutput {
  private List<RecommendedReputationListDto> lists;

  public RecommendedReputationListsApiOutput() {
  }

  public RecommendedReputationListsApiOutput(List<RecommendedReputationListDto> lists) {
    this.lists = lists;
  }

  public List<RecommendedReputationListDto> getLists() {
    return lists;
  }

  public void setLists(List<RecommendedReputationListDto> lists) {
    this.lists = lists;
  }
}

@TypeScriptType
class RecommendedReputationListDto {
  private String id;
  private ReputationListType type;
  private String url;
  private String description;
  private String approxEntries;

  public RecommendedReputationListDto() {
  }

  public RecommendedReputationListDto(String id, ReputationListType type, String url, String description, String approxEntries) {
    this.id = id;
    this.type = type;
    this.url = url;
    this.description = description;
    this.approxEntries = approxEntries;
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

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getApproxEntries() {
    return approxEntries;
  }

  public void setApproxEntries(String approxEntries) {
    this.approxEntries = approxEntries;
  }
}
