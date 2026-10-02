package net.pieroxy.mom.api.metadata;

import net.pieroxy.mom.services.IServiceProvider;

/**
 * Base for every endpoint that requires a logged-in session — everything except {@code LoginApi}
 * (no session to check yet) and {@code SessionApi} (its whole job is answering "is this session
 * still valid," including when it isn't, so it can never require one itself). {@link #process}
 * checks {@code input.getSessionId()} before an endpoint's own logic ever runs, via a {@code
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
  public final O process(I input) throws Exception {
    if (!serviceProvider.getSessionService().isValid(input.getSessionId())) {
      throw new NotAuthenticatedException("Session expired.");
    }
    if (!allowedWithTemporaryPassword() && serviceProvider.getSettingsService().isWebServerPasswordTemporary()) {
      throw new PasswordChangeRequiredException();
    }
    return processAuthenticated(input);
  }

  protected boolean allowedWithTemporaryPassword() {
    return false;
  }

  public abstract O processAuthenticated(I input) throws Exception;
}
