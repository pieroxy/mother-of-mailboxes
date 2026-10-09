package net.pieroxy.mom.api.implementations.setup;

import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.general.ReputationListConfig;
import net.pieroxy.mom.detection.reputation.RecommendedReputationLists;
import net.pieroxy.mom.services.IServiceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Setup wizard's last step: adds the chosen recommended reputation lists and ends the setup. */
@Endpoint(method = ApiMethod.POST)
public class CompleteSetupApi extends AbstractAuthenticatedEndpoint<CompleteSetupApiInput, CompleteSetupApiOutput> {
  private final static Logger LOGGER = Logger.getLogger(CompleteSetupApi.class.getName());

  public CompleteSetupApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public CompleteSetupApiOutput processAuthenticated(CompleteSetupApiInput input) {
    List<String> ids = input.getReputationListIds() != null ? input.getReputationListIds() : List.of();
    List<ReputationListConfig> lists = new ArrayList<>();
    for (String id : ids) {
      ReputationListConfig recommended = RecommendedReputationLists.byId(id)
          .orElseThrow(() -> new IllegalArgumentException("Unknown recommended reputation list: " + id))
          .config();
      ReputationListConfig copy = new ReputationListConfig();
      copy.setId(recommended.getId());
      copy.setType(recommended.getType());
      copy.setUrl(recommended.getUrl());
      copy.setRefreshHours(recommended.getRefreshHours());
      copy.setScore(recommended.getScore());
      lists.add(copy);
    }
    serviceProvider.getSettingsService().completeSetup(lists);
    LOGGER.info("Setup complete" + (ids.isEmpty() ? ", no reputation list added" : ", reputation lists added: " + ids));
    return new CompleteSetupApiOutput();
  }
}

@TypeScriptType
class CompleteSetupApiInput extends AuthenticatedApiInput {
  private List<String> reputationListIds;

  public List<String> getReputationListIds() {
    return reputationListIds;
  }

  public void setReputationListIds(List<String> reputationListIds) {
    this.reputationListIds = reputationListIds;
  }
}

@TypeScriptType
class CompleteSetupApiOutput {
}
