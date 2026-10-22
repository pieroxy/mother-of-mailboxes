package net.pieroxy.mom.api.implementations.reputation;

import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.config.general.ReputationListConfig;
import net.pieroxy.mom.detection.reputation.ReputationRegistry;
import net.pieroxy.mom.detection.reputation.ReputationRegistryHolder;

/**
 * Downloads a reputation list right away (see {@link ReputationRegistry#refreshNow}). A failed
 * download is an expected outcome (source down, no network), so it travels in the output rather
 * than as an API error.
 */
@Endpoint(method = ApiMethod.POST)
public class RefreshReputationListApi extends AbstractAuthenticatedEndpoint<RefreshReputationListApiInput, RefreshReputationListApiOutput> {
  public RefreshReputationListApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public RefreshReputationListApiOutput processAuthenticated(RefreshReputationListApiInput input) {
    ReputationRegistry registry = ReputationRegistryHolder.get();
    ReputationListConfig config = registry.getConfiguredLists().stream()
        .filter(c -> c.getId().equals(input.getId()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown reputation list: " + input.getId()));
    String error = null;
    try {
      registry.refreshNow(config.getId());
    } catch (Exception e) {
      error = e.getMessage() != null ? e.getClass().getSimpleName() + ": " + e.getMessage() : e.getClass().getSimpleName();
    }
    return new RefreshReputationListApiOutput(error, new ReputationListDto(config, registry));
  }
}

@TypeScriptType
class RefreshReputationListApiInput extends AuthenticatedApiInput {
  private String id;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }
}

@TypeScriptType
class RefreshReputationListApiOutput {
  /** Why the download failed; null on success. */
  private String error;
  /** The list's state after the attempt. */
  private ReputationListDto list;

  public RefreshReputationListApiOutput() {
  }

  public RefreshReputationListApiOutput(String error, ReputationListDto list) {
    this.error = error;
    this.list = list;
  }

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }

  public ReputationListDto getList() {
    return list;
  }

  public void setList(ReputationListDto list) {
    this.list = list;
  }
}
