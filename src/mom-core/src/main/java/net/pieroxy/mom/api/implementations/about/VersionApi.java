package net.pieroxy.mom.api.implementations.about;

import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.utils.BuildInfo;

/**
 * The running version, for the web UI's footer. Authenticated, so that a page reachable without
 * logging in never tells which release is running.
 */
@Endpoint(method = ApiMethod.GET)
public class VersionApi extends AbstractAuthenticatedEndpoint<VersionApiInput, VersionApiOutput> {
  public VersionApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  /** The footer is also shown on the change-password page. */
  @Override
  protected boolean allowedWithTemporaryPassword() {
    return true;
  }

  @Override
  public VersionApiOutput processAuthenticated(VersionApiInput input) {
    return new VersionApiOutput(BuildInfo.VERSION, BuildInfo.REVISION);
  }
}

@TypeScriptType
class VersionApiInput extends AuthenticatedApiInput {
}

@TypeScriptType
class VersionApiOutput {
  private String version;
  /** Short git revision of the build. */
  private String revision;

  public VersionApiOutput() {
  }

  public VersionApiOutput(String version, String revision) {
    this.version = version;
    this.revision = revision;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getRevision() {
    return revision;
  }

  public void setRevision(String revision) {
    this.revision = revision;
  }
}
