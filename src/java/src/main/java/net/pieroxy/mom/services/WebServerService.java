package net.pieroxy.mom.services;

import net.pieroxy.mom.api.ApiServlet;
import net.pieroxy.mom.config.general.WebServerConfiguration;
import net.pieroxy.mom.utils.logging.OneLineLogFormatter;
import net.pieroxy.mom.webserver.WebappResourceExtractor;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Embedded Tomcat serving the webapp's static files and {@code /api/*}. A no-op if
 * {@code webServer.enabled} is false. Depends on every other service since {@link ApiServlet},
 * wired up in {@link #start()}, may hand any endpoint any of them at request time.
 */
public class WebServerService implements Service {
  private final static Logger LOGGER = Logger.getLogger(WebServerService.class.getName());
  // Below this, compressing costs more (CPU, framing overhead) than it saves on the wire.
  private final static int COMPRESSION_MIN_SIZE_BYTES = 1024;
  private final static long OLD_CONNECTOR_GRACE_MS = 5_000;
  private final static long ADDRESS_SWITCH_DELAY_MS = 1_000;
  private final static long DISABLE_DELAY_MS = 2_000;
  private final static String COMPRESSIBLE_MIME_TYPES =
      "text/html,text/css,application/javascript,image/svg+xml,application/json";

  private final WebServerConfiguration config;
  private final String dataFolder;
  private IServiceProvider serviceProvider;
  private Tomcat tomcat;
  // The connector currently advertised in config.json; during a port change, the previous one
  // keeps running alongside it for OLD_CONNECTOR_GRACE_MS.
  private Connector connector;
  private ScheduledExecutorService scheduler;
  private volatile String addressChangeError;
  private boolean addressSwitchPending;

  public WebServerService(WebServerConfiguration config, String dataFolder) {
    this.config = config;
    this.dataFolder = dataFolder;
  }

  @Override
  public List<Class<? extends Service>> getDependencies() {
    return List.of(SettingsService.class, AccountService.class, SessionService.class);
  }

  @Override
  public void init(IServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  @Override
  public void start() {
    if (config == null || !config.isEnabled()) return;

    // Tomcat's own logging shim (org.apache.juli.logging.DirectJDKLog) force-overwrites the root
    // logger's ConsoleHandler formatter with a plain SimpleFormatter the first time any Tomcat
    // class logs (see its static initializer) — undoing LoggingBootstrap's setup for every logger,
    // MOM's own included, not just Tomcat's. This system property is DirectJDKLog's own supported
    // hook to point it at a different formatter instead; it must be set before any Tomcat class
    // loads, so first thing here.
    System.setProperty("org.apache.juli.formatter", OneLineLogFormatter.class.getName());

    if (config.getHttpPort() <= 0) {
      throw new IllegalStateException("webServer.httpPort configured to an invalid value of " + config.getHttpPort());
    }

    try {
      File webappDir = new File(dataFolder, "webapp-ui");
      WebappResourceExtractor.extract(webappDir);

      tomcat = new Tomcat();
      String tempDir = System.getProperty("java.io.tmpdir");
      if (tempDir != null) {
        tomcat.setBaseDir(tempDir + File.separator + "momTomcat");
      }

      connector = newConnector(config.getHttpPort(), config.getAddress());
      tomcat.setConnector(connector);

      StandardContext ctx = (StandardContext) tomcat.addContext("", webappDir.getAbsolutePath());
      ctx.addWelcomeFile("index.html");
      tomcat.addServlet("", "default", new DefaultServlet());
      ctx.addServletMappingDecoded("/", "default");
      tomcat.addServlet("", "api", new ApiServlet(endpointServiceProvider()));
      ctx.addServletMappingDecoded("/api/*", "api");
      addMimeTypes(ctx);

      tomcat.start();
      LOGGER.info("Web server listening on " + describe(config.getAddress(), config.getHttpPort()));
    } catch (LifecycleException | IOException e) {
      throw new IllegalStateException("Could not start the web server", e);
    }
  }

  /**
   * What API endpoints get: this service's scoped provider, plus this service itself — which it
   * can't declare in {@link #getDependencies()} without creating a cycle.
   */
  IServiceProvider endpointServiceProvider() {
    IServiceProvider scoped = serviceProvider;
    return new IServiceProvider() {
      @Override
      public SettingsService getSettingsService() {
        return scoped.getSettingsService();
      }

      @Override
      public AccountService getAccountService() {
        return scoped.getAccountService();
      }

      @Override
      public SessionService getSessionService() {
        return scoped.getSessionService();
      }

      @Override
      public WebServerService getWebServerService() {
        return WebServerService.this;
      }
    };
  }

  /**
   * Moves the web server to another port without a restart: the new port is opened alongside the
   * current one, and only once it listens is the change saved to config.json and the old port
   * closed — {@link #OLD_CONNECTOR_GRACE_MS} later, so the response to the request asking for the
   * change (served on the old port) still gets out.
   *
   * @throws IllegalArgumentException if the port is invalid or the new port can't be listened on;
   *     nothing has changed then.
   */
  public synchronized void changePort(int newPort) {
    checkRunning();
    if (newPort < 1 || newPort > 65535) {
      throw new IllegalArgumentException("The port must be between 1 and 65535.");
    }
    int oldPort = config.getHttpPort();
    if (newPort == oldPort) {
      throw new IllegalArgumentException("The web server already listens on port " + newPort + ".");
    }
    String address = config.getAddress();
    LOGGER.info("Web server port change requested: " + describe(address, oldPort) + " -> " + describe(address, newPort));

    Connector added;
    try {
      added = startConnector(address, newPort);
    } catch (ListenException e) {
      LOGGER.warning("Web server port change failed: could not listen on " + describe(address, newPort) + " (" + e.getMessage()
          + "). Still listening on " + describe(address, oldPort) + ", config.json unchanged.");
      throw new IllegalArgumentException("Could not listen on port " + newPort + ": " + e.getMessage());
    }
    LOGGER.info("Web server now also listening on " + describe(address, newPort));

    config.setHttpPort(newPort);
    saveOrUndo(added, () -> config.setHttpPort(oldPort), describe(address, newPort), describe(address, oldPort));
    LOGGER.info("Web server port saved to config.json: " + newPort);
    retireCurrentConnector(added, describe(address, oldPort), describe(address, newPort));
  }

  /**
   * Moves the web server to another address (blank = all interfaces) without a restart. When the
   * new address can be listened on alongside the current one, it's done the same way as
   * {@link #changePort}. Otherwise — typically between all interfaces and one of them, which
   * share the port — the switch happens {@link #ADDRESS_SWITCH_DELAY_MS} later, once the response
   * is out: the current address is closed, the new one opened, and the current one reopened if
   * that fails ({@link #getAddressChangeError()} then says why).
   *
   * @return true if the new address already listens, false if the switch is scheduled.
   * @throws IllegalArgumentException if the address isn't one of this machine's; nothing has
   *     changed then.
   */
  public synchronized boolean changeAddress(String requestedAddress) {
    checkRunning();
    String newAddress = requestedAddress == null ? "" : requestedAddress.trim();
    String oldAddress = config.getAddress() == null ? "" : config.getAddress().trim();
    int port = config.getHttpPort();
    if (newAddress.equals(oldAddress)) {
      throw new IllegalArgumentException("The web server already listens on " + describe(newAddress, port) + ".");
    }
    checkLocalAddress(newAddress);
    LOGGER.info("Web server address change requested: " + describe(oldAddress, port) + " -> " + describe(newAddress, port));
    addressChangeError = null;

    Connector added;
    try {
      added = startConnector(newAddress, port);
    } catch (ListenException e) {
      LOGGER.info("Web server cannot listen on " + describe(newAddress, port) + " alongside " + describe(oldAddress, port)
          + " (" + e.getMessage() + "): switching over in " + ADDRESS_SWITCH_DELAY_MS + "ms instead");
      Connector current = connector;
      addressSwitchPending = true;
      scheduler().schedule(() -> switchAddress(current, oldAddress, newAddress, port), ADDRESS_SWITCH_DELAY_MS, TimeUnit.MILLISECONDS);
      return false;
    }
    LOGGER.info("Web server now also listening on " + describe(newAddress, port));

    config.setAddress(newAddress.isEmpty() ? null : newAddress);
    saveOrUndo(added, () -> config.setAddress(oldAddress.isEmpty() ? null : oldAddress), describe(newAddress, port), describe(oldAddress, port));
    LOGGER.info("Web server address saved to config.json: " + describe(newAddress, port));
    retireCurrentConnector(added, describe(oldAddress, port), describe(newAddress, port));
    return true;
  }

  /**
   * Turns the web server off for good: saves {@code enabled=false} to config.json (the rest of
   * the web server's settings and its login are kept), then stops Tomcat
   * {@link #DISABLE_DELAY_MS} later, once the response is out. Only editing config.json and
   * restarting MOM brings it back.
   */
  public synchronized void disable() {
    checkRunning();
    LOGGER.info("Web server disable requested (listening on " + describe(config.getAddress(), config.getHttpPort()) + ")");
    config.setEnabled(false);
    try {
      serviceProvider.getSettingsService().persistConfig();
    } catch (RuntimeException e) {
      config.setEnabled(true);
      LOGGER.log(Level.WARNING, "Web server disable failed: could not save config.json. Still listening on "
          + describe(config.getAddress(), config.getHttpPort()) + ".", e);
      throw new IllegalStateException("Could not save config.json: " + rootCauseMessage(e));
    }
    LOGGER.info("Web server disabled in config.json; stopping in " + DISABLE_DELAY_MS / 1000 + "s");
    connector = null; // refuses any further change from now on (see checkRunning)
    scheduler().schedule(this::stopDisabled, DISABLE_DELAY_MS, TimeUnit.MILLISECONDS);
  }

  private void stopDisabled() {
    if (stopTomcat()) {
      LOGGER.info("Web server stopped. To bring it back: set webServer.enabled to true in config.json and restart MOM.");
    }
  }

  /** @return false if Tomcat was already stopped. */
  private synchronized boolean stopTomcat() {
    if (tomcat == null) return false;
    try {
      tomcat.stop();
      tomcat.destroy();
    } catch (LifecycleException e) {
      LOGGER.log(Level.SEVERE, "Error stopping the web server", e);
    }
    tomcat = null;
    connector = null;
    return true;
  }

  /** Why the last scheduled address switch failed, or null — see {@link #changeAddress}. */
  public String getAddressChangeError() {
    return addressChangeError;
  }

  private synchronized void switchAddress(Connector current, String oldAddress, String newAddress, int port) {
    addressSwitchPending = false;
    tomcat.getService().removeConnector(current);
    destroyQuietly(current);
    LOGGER.info("Web server stopped listening on " + describe(oldAddress, port) + " (switching to " + describe(newAddress, port) + ")");
    try {
      connector = startConnector(newAddress, port);
    } catch (ListenException e) {
      addressChangeError = "Could not listen on " + describe(newAddress, port) + ": " + e.getMessage();
      LOGGER.warning("Web server address change failed: could not listen on " + describe(newAddress, port) + " ("
          + e.getMessage() + "). Going back to " + describe(oldAddress, port) + ", config.json unchanged.");
      try {
        connector = startConnector(oldAddress, port);
        LOGGER.info("Web server listening again on " + describe(oldAddress, port));
      } catch (ListenException e2) {
        connector = null;
        LOGGER.severe("Web server could not listen again on " + describe(oldAddress, port) + " (" + e2.getMessage()
            + "): the web UI is unreachable until MOM is restarted.");
      }
      return;
    }
    LOGGER.info("Web server listening on " + describe(newAddress, port));
    config.setAddress(newAddress.isEmpty() ? null : newAddress);
    try {
      serviceProvider.getSettingsService().persistConfig();
      LOGGER.info("Web server address saved to config.json: " + describe(newAddress, port));
    } catch (RuntimeException e) {
      addressChangeError = "Listening on " + describe(newAddress, port) + ", but config.json could not be saved: " + rootCauseMessage(e);
      LOGGER.log(Level.WARNING, "Web server listening on " + describe(newAddress, port)
          + ", but config.json could not be saved: MOM will use " + describe(oldAddress, port) + " again after a restart.", e);
    }
  }

  /** Saves config.json, or — if that fails — runs {@code undo}, closes {@code added} and throws. */
  private void saveOrUndo(Connector added, Runnable undo, String newDescription, String oldDescription) {
    try {
      serviceProvider.getSettingsService().persistConfig();
    } catch (RuntimeException e) {
      undo.run();
      tomcat.getService().removeConnector(added);
      destroyQuietly(added);
      LOGGER.log(Level.WARNING, "Web server change failed: could not save config.json. Stopped listening on "
          + newDescription + ", still listening on " + oldDescription + ".", e);
      throw new IllegalStateException("Could not save config.json: " + rootCauseMessage(e));
    }
  }

  /** Makes {@code replacement} the current connector and closes the previous one after {@link #OLD_CONNECTOR_GRACE_MS}. */
  private void retireCurrentConnector(Connector replacement, String oldDescription, String newDescription) {
    Connector old = connector;
    connector = replacement;
    scheduler().schedule(() -> closeConnector(old, oldDescription, newDescription), OLD_CONNECTOR_GRACE_MS, TimeUnit.MILLISECONDS);
    LOGGER.info("Web server will stop listening on " + oldDescription + " in " + OLD_CONNECTOR_GRACE_MS / 1000 + "s");
  }

  private synchronized void closeConnector(Connector old, String oldDescription, String newDescription) {
    tomcat.getService().removeConnector(old);
    destroyQuietly(old);
    LOGGER.info("Web server stopped listening on " + oldDescription + " (now on " + newDescription + " only)");
  }

  /** Adds a started connector to Tomcat, or leaves nothing behind and throws. */
  private Connector startConnector(String address, int port) throws ListenException {
    Connector added = newConnector(port, address);
    try {
      checkCanListen(address, port);
      tomcat.getService().addConnector(added);
      if (added.getState() != LifecycleState.STARTED) {
        // Tomcat logs the actual cause itself instead of throwing.
        throw new IllegalStateException("Tomcat could not start it, see the error above");
      }
      return added;
    } catch (IOException | RuntimeException e) {
      tomcat.getService().removeConnector(added);
      destroyQuietly(added);
      throw new ListenException(rootCauseMessage(e));
    }
  }

  private void checkRunning() {
    if (tomcat == null || connector == null) {
      throw new IllegalStateException("The web server is not running.");
    }
    if (addressSwitchPending) {
      throw new IllegalStateException("An address change is still being applied, try again in a moment.");
    }
  }

  /** Blank (all interfaces), loopback, or an address of one of this machine's network interfaces. */
  private static void checkLocalAddress(String address) {
    if (address.isEmpty()) return;
    InetAddress resolved;
    try {
      resolved = InetAddress.getByName(address);
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Unknown address: " + address + ".");
    }
    if (resolved.isAnyLocalAddress() || resolved.isLoopbackAddress()) return;
    try {
      if (NetworkInterface.getByInetAddress(resolved) != null) return;
    } catch (SocketException e) {
      LOGGER.log(Level.FINE, "Could not list network interfaces", e);
    }
    throw new IllegalArgumentException(address + " is not an address of this machine.");
  }

  private static final class ListenException extends Exception {
    ListenException(String reason) {
      super(reason);
    }
  }

  private Connector newConnector(int port, String address) {
    Connector c = new Connector();
    c.setPort(port);
    if (address != null && !address.isBlank()) {
      c.setProperty("address", address);
    }
    c.setProperty("compression", "on");
    c.setProperty("compressionMinSize", String.valueOf(COMPRESSION_MIN_SIZE_BYTES));
    c.setProperty("compressibleMimeType", COMPRESSIBLE_MIME_TYPES);
    // DefaultServlet serves static files via NIO sendfile by default, which writes straight to
    // the socket and bypasses the output filters — including this compression — entirely.
    c.setProperty("useSendfile", "false");
    return c;
  }

  /**
   * Binds and releases the port, for a clear error message: when Tomcat fails to bind, it only
   * logs the cause (with stack traces) and leaves its connector in a FAILED state.
   */
  private static void checkCanListen(String address, int port) throws IOException {
    try (ServerSocket probe = new ServerSocket()) {
      probe.setReuseAddress(true);
      probe.bind(address == null || address.isBlank() ? new InetSocketAddress(port) : new InetSocketAddress(address, port));
    }
  }

  private synchronized ScheduledExecutorService scheduler() {
    if (scheduler == null) {
      scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "web-server-connectors");
        t.setDaemon(true);
        return t;
      });
    }
    return scheduler;
  }

  private static void destroyQuietly(Connector c) {
    try {
      c.destroy();
    } catch (LifecycleException e) {
      LOGGER.log(Level.FINE, "Error destroying connector on port " + c.getPort(), e);
    }
  }

  /** E.g. "127.0.0.1:8080", "[::1]:8080", "*:8080 (all interfaces)". */
  private static String describe(String address, int port) {
    if (address == null || address.isBlank()) return "*:" + port + " (all interfaces)";
    return (address.contains(":") ? "[" + address + "]" : address) + ":" + port;
  }

  private static String rootCauseMessage(Throwable t) {
    Throwable root = t;
    while (root.getCause() != null && root.getCause() != root) root = root.getCause();
    return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
  }

  @Override
  public void destroy() {
    if (scheduler != null) scheduler.shutdownNow();
    stopTomcat();
  }

  private static void addMimeTypes(StandardContext ctx) {
    ctx.addMimeMapping("js", "application/javascript;charset=utf-8");
    ctx.addMimeMapping("html", "text/html;charset=utf-8");
    ctx.addMimeMapping("css", "text/css;charset=utf-8");
    ctx.addMimeMapping("svg", "image/svg+xml");
    ctx.addMimeMapping("png", "image/png");
    ctx.addMimeMapping("ico", "image/x-icon");
    ctx.addMimeMapping("webmanifest", "application/manifest+json");
  }
}
