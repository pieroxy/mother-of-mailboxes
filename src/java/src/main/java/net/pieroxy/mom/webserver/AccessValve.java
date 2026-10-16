package net.pieroxy.mom.webserver;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import net.pieroxy.mom.api.metadata.ServletApiContext;
import net.pieroxy.mom.config.credentials.Credential;
import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.utils.logging.AccessLog;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * The web server's front door, on the engine's pipeline: sees every request on every connector,
 * whether it's for the API, a static file or a path that doesn't exist. As a Tomcat
 * {@link org.apache.catalina.AccessLog}, Tomcat also calls {@link #log} once each response is fully
 * sent — so the byte count includes buffered output — and for requests rejected before the
 * pipeline (e.g. malformed ones).
 */
public class AccessValve extends ValveBase implements org.apache.catalina.AccessLog {
  private final IServiceProvider serviceProvider;
  private boolean requestAttributesEnabled;

  public AccessValve(IServiceProvider serviceProvider) {
    super(true);
    this.serviceProvider = serviceProvider;
  }

  @Override
  public void invoke(Request request, Response response) throws IOException, ServletException {
    getNext().invoke(request, response);
  }

  /** {@code time} is ignored: its unit changed across Tomcat versions, so the duration comes from the request's own start. */
  @Override
  public void log(Request request, Response response, long time) {
    org.apache.coyote.Request coyote = request.getCoyoteRequest();
    ZonedDateTime start = ZonedDateTime.ofInstant(Instant.ofEpochMilli(coyote.getStartTime()), ZoneId.systemDefault());
    long durationMs = Math.max(0, (System.nanoTime() - coyote.getStartTimeNanos()) / 1_000_000);
    AccessLog.record(start, AccessLog.format(request.getRemoteAddr(), sessionUser(request), start, request.getMethod(),
        request.getRequestURI(), request.getQueryString(), request.getProtocol(), response.getStatus(),
        response.getBytesWritten(false), request.getHeader("Referer"), request.getHeader("User-Agent"), durationMs));
  }

  @Override
  public void setRequestAttributesEnabled(boolean requestAttributesEnabled) {
    this.requestAttributesEnabled = requestAttributesEnabled;
  }

  @Override
  public boolean getRequestAttributesEnabled() {
    return requestAttributesEnabled;
  }

  /** The web login's username when the request carries a valid session, null otherwise. */
  private String sessionUser(Request request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return null;
    for (Cookie cookie : cookies) {
      if (ServletApiContext.SESSION_COOKIE.equals(cookie.getName()) && serviceProvider.getSessionService().isValid(cookie.getValue())) {
        Credential credential = serviceProvider.getSettingsService().getWebServerCredential();
        return credential != null ? credential.getUsername() : null;
      }
    }
    return null;
  }
}
