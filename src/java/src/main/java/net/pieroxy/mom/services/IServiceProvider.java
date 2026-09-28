package net.pieroxy.mom.services;

/**
 * What a piece of code that only needs to fetch a service is allowed to see. {@link ServiceProvider}
 * (the concrete class) implements this without restriction — everything else that's handed one,
 * an API endpoint at request time or a {@link Service#init} during startup, gets a view that in
 * general may be narrowed to a specific service's own declared {@link Service#getDependencies()}
 * (see {@link ServiceProvider}'s scoped proxy) — so depend on this interface, not the concrete
 * class, unless you specifically need {@link ServiceProvider#init()}/{@link ServiceProvider#destroy()}.
 */
public interface IServiceProvider {
  SettingsService getSettingsService();

  AccountService getAccountService();

  SessionService getSessionService();

  WebServerService getWebServerService();
}
