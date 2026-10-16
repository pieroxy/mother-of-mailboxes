package net.pieroxy.mom.api.metadata;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.pieroxy.mom.services.SessionService;

/**
 * The session cookie is HttpOnly (out of reach of any script) and SameSite=Strict (never sent
 * along with a request initiated by another site).
 */
public class ServletApiContext implements ApiContext {
  public static final String SESSION_COOKIE = "mom_session";

  private final HttpServletRequest req;
  private final HttpServletResponse res;

  public ServletApiContext(HttpServletRequest req, HttpServletResponse res) {
    this.req = req;
    this.res = res;
  }

  @Override
  public String getRemoteAddr() {
    return req.getRemoteAddr();
  }

  @Override
  public String getSessionId() {
    Cookie[] cookies = req.getCookies();
    if (cookies == null) return null;
    for (Cookie cookie : cookies) {
      if (SESSION_COOKIE.equals(cookie.getName())) return cookie.getValue();
    }
    return null;
  }

  @Override
  public void setSessionCookie(String sessionId, boolean secure) {
    writeCookie(sessionId, (int) SessionService.LIFETIME.toSeconds(), secure);
  }

  @Override
  public void clearSessionCookie(boolean secure) {
    writeCookie("", 0, secure);
  }

  private void writeCookie(String value, int maxAgeSeconds, boolean secure) {
    Cookie cookie = new Cookie(SESSION_COOKIE, value);
    cookie.setPath("/");
    cookie.setMaxAge(maxAgeSeconds);
    cookie.setHttpOnly(true);
    cookie.setSecure(secure);
    cookie.setAttribute("SameSite", "Strict");
    res.addCookie(cookie);
  }
}
