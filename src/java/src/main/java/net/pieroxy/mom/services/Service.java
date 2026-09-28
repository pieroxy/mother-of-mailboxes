package net.pieroxy.mom.services;

import java.util.List;

/**
 * A service hosted by {@link ServiceProvider}. Lifecycle, in order:
 * <ol>
 *   <li>{@link #getDependencies()} — which other services (if any) must be fully {@link #init}ed
 *       and {@link #start()}ed before this one's own turn — see {@link ServiceProvider} for the
 *       topological ordering this drives. Only needed by a service that can't get a sibling
 *       through plain constructor injection (i.e. a circular case, like {@code WebServerService}
 *       needing the very {@link ServiceProvider} that hosts it) — most services have none.
 *   <li>{@link #init(IServiceProvider)} — grab whatever declared dependencies this service needs;
 *       only the services actually returned by {@link #getDependencies()} are reachable through
 *       the argument, anything else throws.
 *   <li>{@link #start()} — do the actual startup work. Every declared dependency is fully started
 *       by now, not just init()ed.
 *   <li>{@link #destroy()} — stop cleanly, in reverse start order.
 * </ol>
 * All four are no-ops by default, for a service that needs none of this.
 */
public interface Service {
  default List<Class<? extends Service>> getDependencies() {
    return List.of();
  }

  default void init(IServiceProvider serviceProvider) {}

  default void start() {}

  default void destroy() {}
}
