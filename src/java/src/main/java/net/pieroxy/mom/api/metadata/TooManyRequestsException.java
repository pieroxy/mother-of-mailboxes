package net.pieroxy.mom.api.metadata;

/**
 * Special-cased by {@link AbstractApiEndpoint} into an HTTP 429 with a {@code Retry-After}
 * header, its message shown to the user as is. Not logged there: the endpoint throwing it knows
 * best what's worth logging.
 */
public class TooManyRequestsException extends RuntimeException {
  private final long retryAfterSeconds;

  public TooManyRequestsException(String message, long retryAfterSeconds) {
    super(message);
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
