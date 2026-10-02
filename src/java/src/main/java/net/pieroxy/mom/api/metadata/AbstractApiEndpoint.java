package net.pieroxy.mom.api.metadata;

import com.google.gson.Gson;
import net.pieroxy.mom.api.model.ApiResponse;
import net.pieroxy.mom.utils.reflection.GenericTypeArgumentResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractApiEndpoint<I, O> implements ApiEndpoint {
  private final static Gson GSON = new Gson();

  private final Logger logger = Logger.getLogger(getClass().getName());
  private final Class<I> inputType;

  public AbstractApiEndpoint() {
    inputType = resolveInputType();
  }

  public abstract O process(I input) throws Exception;

  @Override
  public final void process(HttpServletRequest req, HttpServletResponse res) {
    res.setContentType("application/json;charset=UTF-8");
    ApiResponse<O> response;
    try {
      I input = readInput(req);
      response = ApiResponse.buildOkResult(process(input));
    } catch (NotAuthenticatedException e) {
      // Routine (a restart, or just an old tab) — not a bug, so no SEVERE stack trace for it.
      res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response = ApiResponse.buildErrResult(e.getMessage());
    } catch (PasswordChangeRequiredException e) {
      res.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response = ApiResponse.buildErrResult(e.getMessage());
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Endpoint " + getClass().getSimpleName() + " failed", e);
      response = ApiResponse.buildErrResult(e.getMessage());
    }
    writeResponse(res, response);
  }

  private I readInput(HttpServletRequest req) throws IOException {
    if ("GET".equals(req.getMethod())) {
      return GSON.fromJson(req.getParameter("input"), inputType);
    }
    return GSON.fromJson(req.getReader(), inputType);
  }

  private void writeResponse(HttpServletResponse res, ApiResponse<O> response) {
    try {
      res.getWriter().write(GSON.toJson(response));
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Could not write response for " + getClass().getSimpleName(), e);
    }
  }

  /** See {@link GenericTypeArgumentResolver} for why a subclass one level down (e.g. {@link AbstractAuthenticatedEndpoint}) needs more than reading the leaf class's own type arguments. */
  @SuppressWarnings("unchecked")
  private Class<I> resolveInputType() {
    return (Class<I>) GenericTypeArgumentResolver.resolve(getClass(), AbstractApiEndpoint.class, 0);
  }
}
