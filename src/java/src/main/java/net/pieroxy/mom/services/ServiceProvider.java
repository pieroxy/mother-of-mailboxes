package net.pieroxy.mom.services;

import java.util.List;

/**
 * Hosts every service instance the API layer depends on ({@link SettingsService},
 * {@link AccountService}, {@link SessionService}), and manages their startup/shutdown lifecycle —
 * nothing else. Actual behavior lives on the services themselves; an API endpoint asks this only
 * for the one it needs (e.g. {@code getAccountService().getAccounts()}), never through a method
 * on this class directly.
 */
public class ServiceProvider {
  private final SettingsService settingsService;
  private final AccountService accountService;
  private final SessionService sessionService;
  // Init order: settings before accounts (accounts need resolved credentials and the live
  // config), session last (independent of the other two either way).
  private final List<Service> services;

  public ServiceProvider(SettingsService settingsService, AccountService accountService, SessionService sessionService) {
    this.settingsService = settingsService;
    this.accountService = accountService;
    this.sessionService = sessionService;
    this.services = List.of(settingsService, accountService, sessionService);
  }

  public SettingsService getSettingsService() {
    return settingsService;
  }

  public AccountService getAccountService() {
    return accountService;
  }

  public SessionService getSessionService() {
    return sessionService;
  }

  public void init() {
    services.forEach(Service::init);
  }

  /** Reverse of {@link #init}'s order: whatever started last is stopped first. */
  public void destroy() {
    for (int i = services.size() - 1; i >= 0; i--) {
      services.get(i).destroy();
    }
  }
}
