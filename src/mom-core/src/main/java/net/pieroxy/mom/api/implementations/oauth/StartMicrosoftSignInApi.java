package net.pieroxy.mom.api.implementations.oauth;

import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.services.OAuthSignInService;
import net.pieroxy.mom.utils.mail.oauth.MicrosoftIdentity;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Starts signing in to a Microsoft mailbox: the user types {@code userCode} at
 * {@code verificationUri}, while the webapp polls MicrosoftSignInStatusApi with {@code signInId}.
 */
@Endpoint(method = ApiMethod.POST)
public class StartMicrosoftSignInApi extends AbstractAuthenticatedEndpoint<StartMicrosoftSignInApiInput, StartMicrosoftSignInApiOutput> {
  private static final Pattern CLIENT_ID = Pattern.compile("[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}");
  // Microsoft's answer when the app registration doesn't allow public client flows.
  private static final String CONFIDENTIAL_CLIENT_ERROR = "AADSTS7000218";

  public StartMicrosoftSignInApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public StartMicrosoftSignInApiOutput processAuthenticated(StartMicrosoftSignInApiInput input) throws IOException {
    String clientId = input.getClientId() != null && !input.getClientId().isBlank() ? input.getClientId().trim() : MicrosoftIdentity.MOM_CLIENT_ID;
    if (!CLIENT_ID.matcher(clientId).matches()) {
      throw new IllegalArgumentException("The application (client) ID looks like 00000000-0000-0000-0000-000000000000.");
    }
    OAuthSignInService.Started started;
    try {
      started = serviceProvider.getOAuthSignInService().start(clientId);
    } catch (IllegalArgumentException e) {
      if (e.getMessage().contains(CONFIDENTIAL_CLIENT_ERROR)) {
        throw new IllegalArgumentException(e.getMessage() + " Set \"Allow public client flows\" to Yes in the app's Authentication page.");
      }
      throw e;
    } catch (IOException e) {
      throw new IOException("Could not reach Microsoft: " + e.getMessage(), e);
    }
    StartMicrosoftSignInApiOutput output = new StartMicrosoftSignInApiOutput();
    output.setSignInId(started.signInId());
    output.setUserCode(started.userCode());
    output.setVerificationUri(started.verificationUri());
    output.setExpiresInSeconds(started.expiresInSeconds());
    output.setIntervalSeconds(started.intervalSeconds());
    return output;
  }
}

@TypeScriptType
class StartMicrosoftSignInApiInput extends AuthenticatedApiInput {
  /** The "Application (client) ID" of the user's own app registration. Blank: MOM's. */
  private String clientId;

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }
}

@TypeScriptType
class StartMicrosoftSignInApiOutput {
  private String signInId;
  private String userCode;
  private String verificationUri;
  private int expiresInSeconds;
  /** How often to poll MicrosoftSignInStatusApi. */
  private int intervalSeconds;

  public String getSignInId() {
    return signInId;
  }

  public void setSignInId(String signInId) {
    this.signInId = signInId;
  }

  public String getUserCode() {
    return userCode;
  }

  public void setUserCode(String userCode) {
    this.userCode = userCode;
  }

  public String getVerificationUri() {
    return verificationUri;
  }

  public void setVerificationUri(String verificationUri) {
    this.verificationUri = verificationUri;
  }

  public int getExpiresInSeconds() {
    return expiresInSeconds;
  }

  public void setExpiresInSeconds(int expiresInSeconds) {
    this.expiresInSeconds = expiresInSeconds;
  }

  public int getIntervalSeconds() {
    return intervalSeconds;
  }

  public void setIntervalSeconds(int intervalSeconds) {
    this.intervalSeconds = intervalSeconds;
  }
}
