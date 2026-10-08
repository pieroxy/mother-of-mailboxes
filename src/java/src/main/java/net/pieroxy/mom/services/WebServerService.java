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
import java.net.InetSocketAddress;
import java.net.ServerSocket;
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
    if (tomcat == null) {
      throw new IllegalStateException("The web server is not running.");
    }
    if (newPort < 1 || newPort > 65535) {
      throw new IllegalArgumentException("The port must be between 1 and 65535.");
    }
    int oldPort = config.getHttpPort();
    if (newPort == oldPort) {
      throw new IllegalArgumentException("The web server already listens on port " + newPort + ".");
    }
    String address = config.getAddress();
    LOGGER.info("Web server port change requested: " + describe(address, oldPort) + " -> " + describe(address, newPort));

    Connector added = newConnector(newPort, address);
    try {
      checkCanListen(address, newPort);
      tomcat.getService().addConnector(added);
      if (added.getState() != LifecycleState.STARTED) {
        // Tomcat logs the actual cause itself instead of throwing.
        throw new IllegalStateException("Tomcat could not start it, see the error above");
      }
    } catch (IOException | RuntimeException e) {
      tomcat.getService().removeConnector(added);
      destroyQuietly(added);
      String reason = rootCauseMessage(e);
      LOGGER.warning("Web server port change failed: could not listen on " + describe(address, newPort) + " (" + reason
          + "). Still listening on " + describe(address, oldPort) + ", config.json unchanged.");
      throw new IllegalArgumentException("Could not listen on port " + newPort + ": " + reason);
    }
    LOGGER.info("Web server now also listening on " + describe(address, newPort));

    config.setHttpPort(newPort);
    try {
      serviceProvider.getSettingsService().persistConfig();
    } catch (RuntimeException e) {
      config.setHttpPort(oldPort);
      tomcat.getService().removeConnector(added);
      destroyQuietly(added);
      LOGGER.log(Level.WARNING, "Web server port change failed: could not save config.json. Stopped listening on "
          + describe(address, newPort) + ", still listening on " + describe(address, oldPort) + ".", e);
      throw new IllegalStateException("Could not save config.json: " + rootCauseMessage(e));
    }
    LOGGER.info("Web server port saved to config.json: " + newPort);

    Connector old = connector;
    connector = added;
    scheduler().schedule(() -> closeConnector(old, describe(address, oldPort), newPort), OLD_CONNECTOR_GRACE_MS, TimeUnit.MILLISECONDS);
    LOGGER.info("Web server will stop listening on " + describe(address, oldPort) + " in " + OLD_CONNECTOR_GRACE_MS / 1000 + "s");
  }

  private synchronized void closeConnector(Connector old, String description, int newPort) {
    tomcat.getService().removeConnector(old);
    destroyQuietly(old);
    LOGGER.info("Web server stopped listening on " + description + " (now on port " + newPort + " only)");
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
        Thread t = new Thread(r, "web-server-connector-closer");
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
    if (tomcat == null) return;
    try {
      tomcat.stop();
      tomcat.destroy();
    } catch (LifecycleException e) {
      LOGGER.log(Level.SEVERE, "Error stopping the web server", e);
    }
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
