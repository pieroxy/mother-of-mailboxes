package net.pieroxy.mom.api.implementations.oauth;

import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.services.OAuthSignInService;

/**
 * Where a sign-in started by StartMicrosoftSignInApi stands. Once {@code SIGNED_IN}, its ID stands
 * for the login in the other endpoints' {@code oauthSignInId}, for an hour.
 */
@Endpoint(method = ApiMethod.POST)
public class MicrosoftSignInStatusApi extends AbstractAuthenticatedEndpoint<MicrosoftSignInStatusApiInput, MicrosoftSignInStatusApiOutput> {
  public MicrosoftSignInStatusApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public MicrosoftSignInStatusApiOutput processAuthenticated(MicrosoftSignInStatusApiInput input) {
    OAuthSignInService.Status status = serviceProvider.getOAuthSignInService().poll(input.getSignInId());
    MicrosoftSignInStatusApiOutput output = new MicrosoftSignInStatusApiOutput();
    output.setState(status.state());
    output.setUsername(status.username());
    output.setMessage(status.message());
    return output;
  }
}

@TypeScriptType
class MicrosoftSignInStatusApiInput extends AuthenticatedApiInput {
  private String signInId;

  public String getSignInId() {
    return signInId;
  }

  public void setSignInId(String signInId) {
    this.signInId = signInId;
  }
}

@TypeScriptType
class MicrosoftSignInStatusApiOutput {
  private OAuthSignInService.SignInState state;
  /** The signed-in account's address, when SIGNED_IN; may be null. */
  private String username;
  /** Why, when FAILED. */
  private String message;

  public OAuthSignInService.SignInState getState() {
    return state;
  }

  public void setState(OAuthSignInService.SignInState state) {
    this.state = state;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
