package net.pieroxy.mom.api.metadata;

/**
 * Thrown by {@link AbstractAuthenticatedEndpoint} while the web login's password is still
 * temporary. Turned into an HTTP 403 by {@link AbstractApiEndpoint}, so the frontend can route to
 * the change-password page.
 */
public class PasswordChangeRequiredException extends RuntimeException {
  public PasswordChangeRequiredException() {
    super("Password change required.");
  }
}
