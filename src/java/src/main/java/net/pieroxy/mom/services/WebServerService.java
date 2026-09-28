package net.pieroxy.mom.services;

import net.pieroxy.mom.api.ApiServlet;
import net.pieroxy.mom.config.general.WebServerConfiguration;
import net.pieroxy.mom.utils.logging.OneLineLogFormatter;
import net.pieroxy.mom.webserver.WebappResourceExtractor;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;

import java.io.File;
import java.io.IOException;
import java.util.List;
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
  private final static String COMPRESSIBLE_MIME_TYPES =
      "text/html,text/css,application/javascript,image/svg+xml,application/json";

  private final WebServerConfiguration config;
  private final String dataFolder;
  private IServiceProvider serviceProvider;
  private Tomcat tomcat;

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

      Connector connector = new Connector();
      connector.setPort(config.getHttpPort());
      tomcat.setConnector(connector);
      if (config.getAddress() != null && !config.getAddress().isBlank()) {
        connector.setProperty("address", config.getAddress());
      }
      connector.setProperty("compression", "on");
      connector.setProperty("compressionMinSize", String.valueOf(COMPRESSION_MIN_SIZE_BYTES));
      connector.setProperty("compressibleMimeType", COMPRESSIBLE_MIME_TYPES);
      // DefaultServlet serves static files via NIO sendfile by default, which writes straight to
      // the socket and bypasses the output filters — including this compression — entirely.
      connector.setProperty("useSendfile", "false");

      StandardContext ctx = (StandardContext) tomcat.addContext("", webappDir.getAbsolutePath());
      ctx.addWelcomeFile("index.html");
      tomcat.addServlet("", "default", new DefaultServlet());
      ctx.addServletMappingDecoded("/", "default");
      tomcat.addServlet("", "api", new ApiServlet(serviceProvider));
      ctx.addServletMappingDecoded("/api/*", "api");
      addMimeTypes(ctx);

      tomcat.start();
      LOGGER.info("Web server started on port " + config.getHttpPort());
    } catch (LifecycleException | IOException e) {
      throw new IllegalStateException("Could not start the web server", e);
    }
  }

  @Override
  public void destroy() {
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
  }
}
