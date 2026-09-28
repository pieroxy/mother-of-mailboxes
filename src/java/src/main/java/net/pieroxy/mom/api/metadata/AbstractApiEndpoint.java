package net.pieroxy.mom.api.metadata;

import com.google.gson.Gson;
import net.pieroxy.mom.api.model.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;
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

  /**
   * Walks up from the concrete endpoint class to {@code AbstractApiEndpoint} itself, resolving
   * {@code I} along the way. A single {@code extends AbstractApiEndpoint<Foo, Bar>} would need
   * nothing more than reading that class's own type arguments, but an intermediate generic
   * superclass (e.g. {@link AbstractAuthenticatedEndpoint}{@code <I, O>}) only knows *its own*
   * type variables at that point, not the leaf class's concrete types — so each level's variables
   * are bound to whatever the level below supplied, and that binding is consulted once we reach
   * {@code AbstractApiEndpoint} and find its argument is itself one of those variables.
   */
  @SuppressWarnings("unchecked")
  private Class<I> resolveInputType() {
    Map<TypeVariable<?>, Type> bindings = new HashMap<>();
    Class<?> current = getClass();
    while (true) {
      Class<?> rawSuperclass = current.getSuperclass();
      if (rawSuperclass == null) {
        throw new IllegalStateException("Could not resolve input type for " + getClass());
      }
      Type genericSuperclass = current.getGenericSuperclass();
      Type[] actualArgs = (genericSuperclass instanceof ParameterizedType parameterizedType)
          ? parameterizedType.getActualTypeArguments() : new Type[0];

      if (rawSuperclass == AbstractApiEndpoint.class) {
        Type inputArg = actualArgs[0];
        Type resolved = (inputArg instanceof TypeVariable<?>) ? bindings.get(inputArg) : inputArg;
        if (!(resolved instanceof Class<?>)) {
          throw new IllegalStateException("Could not resolve a concrete input type for " + getClass() + " (got " + resolved + ")");
        }
        return (Class<I>) resolved;
      }

      TypeVariable<?>[] params = rawSuperclass.getTypeParameters();
      for (int i = 0; i < params.length && i < actualArgs.length; i++) {
        Type arg = actualArgs[i];
        // The argument supplied here may itself be a variable from the level below (not our
        // case today, since every leaf endpoint supplies concrete types directly, but resolving
        // through bindings keeps this correct at any depth) rather than a concrete class.
        bindings.put(params[i], (arg instanceof TypeVariable<?>) ? bindings.getOrDefault(arg, arg) : arg);
      }
      current = rawSuperclass;
    }
  }
}
