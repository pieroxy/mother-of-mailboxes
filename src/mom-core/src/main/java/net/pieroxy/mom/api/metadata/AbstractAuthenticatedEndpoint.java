package net.pieroxy.mom.api.metadata;

import net.pieroxy.mom.services.IServiceProvider;

/**
 * Base for every endpoint that requires a logged-in session — everything except {@code LoginApi}
 * (no session to check yet), {@code SessionApi} (its whole job is answering "is this session
 * still valid," including when it isn't, so it can never require one itself) and {@code LogoutApi}.
 * {@link #process} checks the session cookie before an endpoint's own logic ever runs, via a {@code
 * final} override, so extending this instead of {@link AbstractApiEndpoint} directly is the only
 * thing an endpoint needs to do — there's no check to forget. Same for a temporary web login
 * password (see {@code Credential#isTemporary}): refused unless {@link #allowedWithTemporaryPassword}.
 */
public abstract class AbstractAuthenticatedEndpoint<I extends AuthenticatedApiInput, O> extends AbstractApiEndpoint<I, O> {
  protected final IServiceProvider serviceProvider;

  protected AbstractAuthenticatedEndpoint(IServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  @Override
  public final O process(I input, ApiContext context) throws Exception {
    String sessionId = context.getSessionId();
    if (!serviceProvider.getSessionService().isValid(sessionId)) {
      throw new NotAuthenticatedException("Session expired.");
    }
    serviceProvider.getSessionService().recordRequest(sessionId, context.getRemoteAddr(), context.getUserAgent());
    if (!allowedWithTemporaryPassword() && serviceProvider.getSettingsService().isWebServerPasswordTemporary()) {
      throw new PasswordChangeRequiredException();
    }
    input.setSessionId(sessionId);
    boolean secureCookie = serviceProvider.getSettingsService().isSessionCookieSecure();
    O output = processAuthenticated(input);
    // Re-issued so the caller's browser follows a Secure flag change right away.
    boolean nowSecureCookie = serviceProvider.getSettingsService().isSessionCookieSecure();
    if (nowSecureCookie != secureCookie) {
      context.setSessionCookie(sessionId, nowSecureCookie);
    }
    return output;
  }

  protected boolean allowedWithTemporaryPassword() {
    return false;
  }

  public abstract O processAuthenticated(I input) throws Exception;
}
