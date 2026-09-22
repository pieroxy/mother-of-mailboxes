package net.pieroxy.mom.api;

import net.pieroxy.mom.api.metadata.ApiEndpoint;
import net.pieroxy.mom.api.metadata.ApiMethod;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.utils.reflection.GetAccessibleClasses;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Discovers {@link Endpoint}-annotated classes under net.pieroxy.mom and routes /api/&lt;Name&gt; to them. */
public class ApiServlet extends HttpServlet {
  private final static Logger LOGGER = Logger.getLogger(ApiServlet.class.getName());
  private final static String API_PATH_PREFIX = "/api/";
  private final static String ENDPOINT_CLASS_SUFFIX = "Api";

  private final ServiceProvider serviceProvider;
  private Map<String, ApiEndpoint> endpoints;

  public ApiServlet(ServiceProvider serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
    dispatch(req, resp, ApiMethod.GET);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
    dispatch(req, resp, ApiMethod.POST);
  }

  private void dispatch(HttpServletRequest req, HttpServletResponse resp, ApiMethod methodCalled) {
    String uri = req.getRequestURI();
    String className = uri.substring(API_PATH_PREFIX.length()) + ENDPOINT_CLASS_SUFFIX;
    ApiEndpoint endpoint = getOrDiscoverEndpoints().get(className);
    if (endpoint == null) {
      LOGGER.warning("No API endpoint found for " + uri);
      resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    Endpoint metadata = endpoint.getClass().getAnnotation(Endpoint.class);
    if (metadata.method() != methodCalled) {
      LOGGER.warning("Endpoint " + uri + " is not accessible through " + methodCalled);
      resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    endpoint.process(req, resp);
  }

  private synchronized Map<String, ApiEndpoint> getOrDiscoverEndpoints() {
    if (endpoints == null) {
      endpoints = discoverEndpoints();
    }
    return endpoints;
  }

  private Map<String, ApiEndpoint> discoverEndpoints() {
    Map<String, ApiEndpoint> discovered = new HashMap<>();
    try {
      List<Class<?>> classes = GetAccessibleClasses.getClasses("net.pieroxy.mom");
      for (Class<?> c : classes) {
        if (ApiEndpoint.class.isAssignableFrom(c) && c.isAnnotationPresent(Endpoint.class)) {
          discovered.put(c.getSimpleName(), instantiate(c));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Could not discover API endpoints", e);
    }
    LOGGER.info("API endpoints found: " + discovered.keySet());
    return discovered;
  }

  private ApiEndpoint instantiate(Class<?> c) throws Exception {
    for (Constructor<?> constructor : c.getConstructors()) {
      if (constructor.getParameterCount() == 0) {
        return (ApiEndpoint) constructor.newInstance();
      }
      if (constructor.getParameterCount() == 1 && constructor.getParameterTypes()[0] == ServiceProvider.class) {
        return (ApiEndpoint) constructor.newInstance(serviceProvider);
      }
    }
    throw new IllegalStateException("Could not instantiate API endpoint " + c.getName()
        + ": no no-arg or (ServiceProvider) constructor.");
  }
}
