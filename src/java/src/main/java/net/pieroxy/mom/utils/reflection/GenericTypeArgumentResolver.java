package net.pieroxy.mom.utils.reflection;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the concrete {@link Class} bound to one of {@code target}'s own type parameters, as
 * seen from {@code leafClass} — needed whenever {@code leafClass} doesn't extend {@code target}
 * directly but through one or more intermediate generic superclasses, each of which only knows
 * *its own* type variables, not the leaf class's concrete types. For example: {@code AccountsApi
 * extends AbstractAuthenticatedEndpoint<AccountsApiInput, AccountsApiOutput> extends
 * AbstractApiEndpoint<I, O>} — reading {@code AbstractApiEndpoint}'s declared type argument at
 * that middle level would just return the type variable {@code I}, not {@code AccountsApiInput}.
 * Shared by {@code AbstractApiEndpoint} (at endpoint construction time) and
 * {@code GenerateTsStubs} (at TS-stub generation time).
 */
public class GenericTypeArgumentResolver {
  public static Class<?> resolve(Class<?> leafClass, Class<?> target, int argIndex) {
    Map<TypeVariable<?>, Type> bindings = new HashMap<>();
    Class<?> current = leafClass;
    while (true) {
      Class<?> rawSuperclass = current.getSuperclass();
      if (rawSuperclass == null) {
        throw new IllegalArgumentException(target.getName() + " is not a superclass of " + leafClass.getName());
      }
      Type genericSuperclass = current.getGenericSuperclass();
      Type[] actualArgs = (genericSuperclass instanceof ParameterizedType parameterizedType)
          ? parameterizedType.getActualTypeArguments() : new Type[0];

      if (rawSuperclass == target) {
        Type arg = actualArgs[argIndex];
        Type resolved = (arg instanceof TypeVariable<?>) ? bindings.get(arg) : arg;
        if (!(resolved instanceof Class<?>)) {
          throw new IllegalStateException("Could not resolve a concrete type argument " + argIndex + " of "
              + target.getName() + " for " + leafClass.getName() + " (got " + resolved + ")");
        }
        return (Class<?>) resolved;
      }

      TypeVariable<?>[] params = rawSuperclass.getTypeParameters();
      for (int i = 0; i < params.length && i < actualArgs.length; i++) {
        Type arg = actualArgs[i];
        // The argument supplied here may itself be a variable from the level below rather than a
        // concrete class (not the case for any endpoint today, since every leaf supplies concrete
        // types directly, but resolving through the existing bindings keeps this correct at any
        // depth).
        bindings.put(params[i], (arg instanceof TypeVariable<?>) ? bindings.getOrDefault(arg, arg) : arg);
      }
      current = rawSuperclass;
    }
  }
}
