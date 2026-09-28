package net.pieroxy.mom.services;

import net.pieroxy.mom.config.credentials.CredentialsFile;
import net.pieroxy.mom.config.general.Configuration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertSame;

/**
 * {@link ServiceProvider#scopedFor}: the view a {@link Service#init} actually receives is
 * restricted to what it declared via {@link Service#getDependencies()} — see
 * {@link ServiceOrderingTest} for the topological-ordering half of the same mechanism.
 */
public class ServiceProviderTest {
  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private static class FakeService implements Service {
    private final List<Class<? extends Service>> dependencies;

    FakeService(List<Class<? extends Service>> dependencies) {
      this.dependencies = dependencies;
    }

    @Override
    public List<Class<? extends Service>> getDependencies() {
      return dependencies;
    }
  }

  /** No webServer section, no configured accounts: init()/destroy() are all no-ops, nothing touches disk or network. */
  private ServiceProvider buildServiceProvider() {
    Configuration config = new Configuration();
    config.setConfigurations(new ArrayList<>());
    File configFile = new File(tmp.getRoot(), "config.json");
    File credentialsFilePath = new File(tmp.getRoot(), "credentials.json");

    SettingsService settingsService = new SettingsService(config, configFile, new CredentialsFile(), credentialsFilePath, tmp.getRoot().getAbsolutePath());
    AccountService accountService = new AccountService(settingsService, new CopyOnWriteArrayList<>());
    SessionService sessionService = new SessionService();
    WebServerService webServerService = new WebServerService(null, tmp.getRoot().getAbsolutePath());
    return new ServiceProvider(settingsService, accountService, sessionService, webServerService);
  }

  @Test
  public void scopedViewExposesADeclaredDependency() {
    ServiceProvider serviceProvider = buildServiceProvider();
    IServiceProvider scoped = serviceProvider.scopedFor(new FakeService(List.of(SettingsService.class)));

    assertSame(serviceProvider.getSettingsService(), scoped.getSettingsService());
  }

  @Test(expected = IllegalStateException.class)
  public void scopedViewThrowsForAnUndeclaredDependency() {
    ServiceProvider serviceProvider = buildServiceProvider();
    IServiceProvider scoped = serviceProvider.scopedFor(new FakeService(List.of(SettingsService.class)));

    scoped.getAccountService();
  }

  @Test(expected = IllegalStateException.class)
  public void scopedViewWithNoDeclaredDependenciesExposesNothing() {
    ServiceProvider serviceProvider = buildServiceProvider();
    IServiceProvider scoped = serviceProvider.scopedFor(new FakeService(List.of()));

    scoped.getSettingsService();
  }

  @Test
  public void initStartsEveryServiceAndDestroyStopsThemCleanly() {
    ServiceProvider serviceProvider = buildServiceProvider();

    serviceProvider.init();
    serviceProvider.destroy();
    // No exception is the whole assertion: WebServerService is disabled (null config) so its own
    // start()/destroy() are no-ops, and there's nothing configured for the other three to act on.
  }
}
