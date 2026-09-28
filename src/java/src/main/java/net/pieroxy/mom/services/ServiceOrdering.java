package net.pieroxy.mom.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Topologically sorts a list of {@link Service}s by each one's declared
 * {@link Service#getDependencies()} — Kahn's algorithm: repeatedly take any not-yet-ordered
 * service whose dependencies are all already ordered. Extracted out of {@link ServiceProvider}
 * (which is itself fixed-shape, not generic over an arbitrary service list) purely so this pure
 * ordering logic is testable on its own, with lightweight fakes, instead of only through the real
 * services.
 */
final class ServiceOrdering {
  private ServiceOrdering() {}

  static List<Service> topologicalOrder(List<Service> services) {
    Map<Class<? extends Service>, Service> byType = new HashMap<>();
    for (Service service : services) {
      byType.put(service.getClass(), service);
    }

    List<Service> ordered = new ArrayList<>();
    Set<Class<? extends Service>> resolved = new HashSet<>();
    List<Service> remaining = new ArrayList<>(services);

    while (!remaining.isEmpty()) {
      boolean progressed = false;
      for (var it = remaining.iterator(); it.hasNext(); ) {
        Service candidate = it.next();
        List<Class<? extends Service>> deps = candidate.getDependencies();
        for (Class<? extends Service> dep : deps) {
          if (!byType.containsKey(dep)) {
            throw new IllegalStateException(candidate.getClass().getSimpleName()
                + " declares a dependency on " + dep.getSimpleName() + ", which is not a registered service");
          }
        }
        if (resolved.containsAll(deps)) {
          ordered.add(candidate);
          resolved.add(candidate.getClass());
          it.remove();
          progressed = true;
        }
      }
      if (!progressed) {
        throw new IllegalStateException("Circular service dependency involving: "
            + remaining.stream().map(s -> s.getClass().getSimpleName()).collect(Collectors.joining(", ")));
      }
    }
    return ordered;
  }
}
