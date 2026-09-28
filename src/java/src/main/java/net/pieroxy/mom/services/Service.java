package net.pieroxy.mom.services;

/**
 * A service hosted by {@link ServiceProvider}: constructed with whatever it depends on (other
 * services, config, credentials), then {@link #init()} once to start any background work
 * (scheduled tasks, loading/migrating persisted state), and {@link #destroy()} once before the
 * process exits to stop it cleanly. Both are no-ops by default, for a service with nothing to
 * start or stop.
 */
public interface Service {
  default void init() {}

  default void destroy() {}
}
