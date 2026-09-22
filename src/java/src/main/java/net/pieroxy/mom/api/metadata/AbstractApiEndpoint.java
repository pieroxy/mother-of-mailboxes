package net.pieroxy.mom.api.metadata;

import com.google.gson.Gson;
import net.pieroxy.mom.api.model.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
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

  @SuppressWarnings("unchecked")
  private Class<I> resolveInputType() {
    Class<?> subClass = getClass();
    while (subClass.getSuperclass() != AbstractApiEndpoint.class) {
      subClass = subClass.getSuperclass();
      if (subClass == null) throw new IllegalStateException("Could not resolve input type for " + getClass());
    }
    ParameterizedType parameterizedType = (ParameterizedType) subClass.getGenericSuperclass();
    return (Class<I>) parameterizedType.getActualTypeArguments()[0];
  }
}
