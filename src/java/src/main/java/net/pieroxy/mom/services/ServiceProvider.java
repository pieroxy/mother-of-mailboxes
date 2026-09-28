package net.pieroxy.mom.services;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Hosts the running {@link SettingsService}, {@link AccountService}, {@link SessionService} and
 * {@link WebServerService}, and manages their startup/shutdown lifecycle.
 * <p>
 * {@link #init()} topologically sorts them by declared {@link Service#getDependencies()}, then for
 * each in turn calls {@link Service#init(IServiceProvider)} (scoped to what it declared — see
 * {@link #scopedFor}) and {@link Service#start()}. {@link #destroy()} stops them in the reverse of
 * that order.
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
   * A view exposing only the services {@code service} declared via {@link Service#getDependencies()};
   * anything else throws. The check is just {@code method.getReturnType()}, since that's exactly
   * the service class each {@link IServiceProvider} getter returns. Package-private: exercised
   * directly by ServiceProviderTest.
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
