package net.pieroxy.mom.services;

import java.util.List;

/**
 * A service hosted by {@link ServiceProvider}. Lifecycle, in order:
 * <ol>
 *   <li>{@link #getDependencies()} — other services that must be fully {@link #init}ed and
 *       {@link #start()}ed before this one's own turn (see {@link ServiceProvider} for the
 *       resulting order). Empty by default.
 *   <li>{@link #init(IServiceProvider)} — the argument only exposes the services declared via
 *       {@link #getDependencies()}; anything else throws.
 *   <li>{@link #start()} — every declared dependency is fully started by now.
 *   <li>{@link #destroy()} — stop cleanly, in reverse start order.
 * </ol>
 * All four are no-ops by default.
 */
public interface Service {
  default List<Class<? extends Service>> getDependencies() {
    return List.of();
  }

  default void init(IServiceProvider serviceProvider) {}

  default void start() {}

  default void destroy() {}
}
