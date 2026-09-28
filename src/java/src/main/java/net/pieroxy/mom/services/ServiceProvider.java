package net.pieroxy.mom.services;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Hosts every service instance the API layer depends on ({@link SettingsService},
 * {@link AccountService}, {@link SessionService}, {@link WebServerService}), and manages their
 * startup/shutdown lifecycle — nothing else. Actual behavior lives on the services themselves; an
 * API endpoint asks {@link IServiceProvider} only for the one it needs, never through a method on
 * this class directly.
 * <p>
 * {@link #init()} topologically sorts the services by each one's declared
 * {@link Service#getDependencies()} (Kahn's algorithm) and, for each in turn, calls
 * {@link Service#init(IServiceProvider)} (with a view scoped to only what it declared — see
 * {@link #scopedFor}) then {@link Service#start()}, before moving to the next — so a service is
 * never started until every service it depends on already has been, fully. {@link #destroy()}
 * unwinds in the reverse of whatever order things actually started in.
 */
public class ServiceProvider implements IServiceProvider {
  private final SettingsService settingsService;
  private final AccountService accountService;
  private final SessionService sessionService;
  private final WebServerService webServerService;
  private final List<Service> services;
  private final List<Service> startedInOrder = new ArrayList<>();

  public ServiceProvider(SettingsService settingsService, AccountService accountService,
                          SessionService sessionService, WebServerService webServerService) {
    this.settingsService = settingsService;
    this.accountService = accountService;
    this.sessionService = sessionService;
    this.webServerService = webServerService;
    this.services = List.of(settingsService, accountService, sessionService, webServerService);
  }

  @Override
  public SettingsService getSettingsService() {
    return settingsService;
  }

  @Override
  public AccountService getAccountService() {
    return accountService;
  }

  @Override
  public SessionService getSessionService() {
    return sessionService;
  }

  @Override
  public WebServerService getWebServerService() {
    return webServerService;
  }

  public void init() {
    for (Service service : ServiceOrdering.topologicalOrder(services)) {
      service.init(scopedFor(service));
      service.start();
      startedInOrder.add(service);
    }
  }

  public void destroy() {
    for (int i = startedInOrder.size() - 1; i >= 0; i--) {
      startedInOrder.get(i).destroy();
    }
  }

  /**
   * A view of this provider exposing only the services {@code service} actually declared via
   * {@link Service#getDependencies()} — anything else throws. Since every {@link IServiceProvider}
   * getter's return type *is* the service class it hands back, the check needs no separate
   * method-to-class table: {@code method.getReturnType()} is exactly what to check. Package-private
   * (instead of private): lets ServiceProviderTest exercise it directly.
   */
  IServiceProvider scopedFor(Service service) {
    Set<Class<? extends Service>> allowed = Set.copyOf(service.getDependencies());
    IServiceProvider real = this;
    return (IServiceProvider) Proxy.newProxyInstance(
        IServiceProvider.class.getClassLoader(),
        new Class<?>[]{IServiceProvider.class},
        (proxy, method, args) -> {
          if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
              case "toString" -> "ScopedServiceProvider[" + service.getClass().getSimpleName() + "]";
              case "hashCode" -> System.identityHashCode(proxy);
              case "equals" -> proxy == args[0];
              default -> throw new UnsupportedOperationException(method.getName());
            };
          }
          @SuppressWarnings("unchecked")
          Class<? extends Service> requested = (Class<? extends Service>) method.getReturnType();
          if (!allowed.contains(requested)) {
            throw new IllegalStateException(service.getClass().getSimpleName() + " accessed " + requested.getSimpleName()
                + " without declaring it in getDependencies()");
          }
          return method.invoke(real, args);
        });
  }
}
