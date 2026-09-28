package net.pieroxy.mom.api.metadata;

import net.pieroxy.mom.services.IServiceProvider;

/**
 * Base for every endpoint that requires a logged-in session — everything except {@code LoginApi}
 * (no session to check yet) and {@code SessionApi} (its whole job is answering "is this session
 * still valid," including when it isn't, so it can never require one itself). {@link #process}
 * checks {@code input.getSessionId()} before an endpoint's own logic ever runs, via a {@code
 * final} override, so extending this instead of {@link AbstractApiEndpoint} directly is the only
 * thing an endpoint needs to do — there's no check to forget.
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
    return processAuthenticated(input);
  }

  public abstract O processAuthenticated(I input) throws Exception;
}
