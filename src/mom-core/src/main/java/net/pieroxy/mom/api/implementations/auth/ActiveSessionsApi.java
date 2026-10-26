package net.pieroxy.mom.api.implementations.auth;

import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.AuthenticatedApiInput;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.services.IServiceProvider;
import net.pieroxy.mom.services.SessionService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/** The web login's open sessions, for the profile page. */
@Endpoint(method = ApiMethod.GET)
public class ActiveSessionsApi extends AbstractAuthenticatedEndpoint<ActiveSessionsApiInput, ActiveSessionsApiOutput> {
  public ActiveSessionsApi(IServiceProvider serviceProvider) {
    super(serviceProvider);
  }

  @Override
  public ActiveSessionsApiOutput processAuthenticated(ActiveSessionsApiInput input) {
    SessionService sessionService = serviceProvider.getSessionService();
    SessionService.Session current = sessionService.get(input.getSessionId());
    Instant now = sessionService.now();
    List<SessionDto> sessions = sessionService.list().stream()
        .map(s -> new SessionDto(s.getPublicId(), s == current, s.getStart().toString(), s.getExpiry().toString(),
            s.getLoginAddress(), s.getLastAddress(), s.getUserAgent(), s.getRequests(),
            Math.max(0, Duration.between(s.getLastRequest(), now).toSeconds())))
        .collect(Collectors.toList());
    return new ActiveSessionsApiOutput(sessions);
  }
}

@TypeScriptType
class ActiveSessionsApiInput extends AuthenticatedApiInput {
}

@TypeScriptType
class ActiveSessionsApiOutput {
  /** Most recently used first. */
  private List<SessionDto> sessions;

  public ActiveSessionsApiOutput() {
  }

  public ActiveSessionsApiOutput(List<SessionDto> sessions) {
    this.sessions = sessions;
  }

  public List<SessionDto> getSessions() {
    return sessions;
  }

  public void setSessions(List<SessionDto> sessions) {
    this.sessions = sessions;
  }
}

@TypeScriptType
class SessionDto {
  /** Identifies the session to RevokeSessionApi. Not the session ID, which must never reach the browser. */
  private String publicId;
  /** The session making this request. */
  private boolean current;
  /** ISO-8601. */
  private String start;
  /** ISO-8601. */
  private String expiry;
  private String loginAddress;
  private String lastAddress;
  /** As sent with the last request; null if none was sent. */
  private String userAgent;
  /** API calls made with this session, the login excluded. */
  private long requests;
  /** Computed server-side, so that a skewed browser clock can't distort it. */
  private long secondsSinceLastRequest;

  public SessionDto() {
  }

  public SessionDto(String publicId, boolean current, String start, String expiry, String loginAddress,
                    String lastAddress, String userAgent, long requests, long secondsSinceLastRequest) {
    this.publicId = publicId;
    this.current = current;
    this.start = start;
    this.expiry = expiry;
    this.loginAddress = loginAddress;
    this.lastAddress = lastAddress;
    this.userAgent = userAgent;
    this.requests = requests;
    this.secondsSinceLastRequest = secondsSinceLastRequest;
  }

  public String getPublicId() {
    return publicId;
  }

  public void setPublicId(String publicId) {
    this.publicId = publicId;
  }

  public boolean isCurrent() {
    return current;
  }

  public void setCurrent(boolean current) {
    this.current = current;
  }

  public String getStart() {
    return start;
  }

  public void setStart(String start) {
    this.start = start;
  }

  public String getExpiry() {
    return expiry;
  }

  public void setExpiry(String expiry) {
    this.expiry = expiry;
  }

  public String getLoginAddress() {
    return loginAddress;
  }

  public void setLoginAddress(String loginAddress) {
    this.loginAddress = loginAddress;
  }

  public String getLastAddress() {
    return lastAddress;
  }

  public void setLastAddress(String lastAddress) {
    this.lastAddress = lastAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  public long getRequests() {
    return requests;
  }

  public void setRequests(long requests) {
    this.requests = requests;
  }

  public long getSecondsSinceLastRequest() {
    return secondsSinceLastRequest;
  }

  public void setSecondsSinceLastRequest(long secondsSinceLastRequest) {
    this.secondsSinceLastRequest = secondsSinceLastRequest;
  }
}
