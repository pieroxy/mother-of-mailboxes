package net.pieroxy.imf.webserver;

import net.pieroxy.imf.api.ApiServlet;
import net.pieroxy.imf.api.ServiceProvider;
import net.pieroxy.imf.config.general.WebServerConfiguration;
import net.pieroxy.imf.utils.logging.OneLineLogFormatter;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Embedded Tomcat serving the webapp's static files, débrayable via {@code webServer.enabled}. */
public class WebServerRunner {
  private final static Logger LOGGER = Logger.getLogger(WebServerRunner.class.getName());
  // Below this, compressing costs more (CPU, framing overhead) than it saves on the wire.
  private final static int COMPRESSION_MIN_SIZE_BYTES = 1024;
  private final static String COMPRESSIBLE_MIME_TYPES =
      "text/html,text/css,application/javascript,image/svg+xml,application/json";

  public static Tomcat start(WebServerConfiguration config, String dataFolder, ServiceProvider serviceProvider) throws LifecycleException, IOException {
    // Tomcat's own logging shim (org.apache.juli.logging.DirectJDKLog) force-overwrites the root
    // logger's ConsoleHandler formatter with a plain SimpleFormatter the first time any Tomcat
    // class logs (see its static initializer) — undoing LoggingBootstrap's setup for every logger,
    // IMF's own included, not just Tomcat's. This system property is DirectJDKLog's own supported
    // hook to point it at a different formatter instead; it must be set before any Tomcat class
    // loads, so first thing here.
    System.setProperty("org.apache.juli.formatter", OneLineLogFormatter.class.getName());

    if (config.getHttpPort() <= 0) {
      throw new IllegalStateException("webServer.httpPort configured to an invalid value of " + config.getHttpPort());
    }

    File webappDir = new File(dataFolder, "webapp-ui");
    WebappResourceExtractor.extract(webappDir);

    Tomcat tomcat = new Tomcat();
    String tempDir = System.getProperty("java.io.tmpdir");
    if (tempDir != null) {
      tomcat.setBaseDir(tempDir + File.separator + "imfTomcat");
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
    return tomcat;
  }

  public static void stop(Tomcat tomcat) {
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
