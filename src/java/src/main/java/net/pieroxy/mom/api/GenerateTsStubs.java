package net.pieroxy.mom.api;

import net.pieroxy.mom.api.metadata.AbstractApiEndpoint;
import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiEndpoint;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.utils.reflection.GetAccessibleClasses;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.stream.Collectors;

/** Not wired into the Maven build yet. Takes the output .ts path as args[0]. */
public class GenerateTsStubs {
  private static BufferedWriter writer;
  private static final List<String> later = new ArrayList<>();

  private static void write(String s) throws IOException {
    writer.write(s);
    writer.newLine();
  }

  private static void writeLater(String s) {
    later.add(s);
  }

  public static void main(String[] args) throws Exception {
    File out = new File(args[0]);
    writer = new BufferedWriter(new FileWriter(out));

    write("/* tslint:disable */");
    write("/* eslint-disable */");
    write("// Generated on " + new Date());
    write("import { Api } from \"../utils/api/Api\";");
    writeLater("export class ApiEndpoints {");

    Set<String> toImport = new HashSet<>();
    List<Class<?>> allClasses = GetAccessibleClasses.getClasses("net.pieroxy.mom");
    for (Class<?> c : allClasses) {
      if (ApiEndpoint.class.isAssignableFrom(c) && c.isAnnotationPresent(Endpoint.class)) {
        System.out.println("Processing " + c.getName());
        Endpoint endpoint = c.getAnnotation(Endpoint.class);
        String rawInput = getType(c, 0);
        String output = getType(c, 1);
        String name = c.getSimpleName().substring(0, c.getSimpleName().length() - "Api".length());
        if (!rawInput.equals("any")) toImport.add(rawInput);
        if (!output.equals("any")) toImport.add(output);

        // AbstractAuthenticatedEndpoint attaches sessionId itself (see Api.call) — callers never
        // set it themselves, so it's typed out of what they're required to pass here.
        boolean authenticated = AbstractAuthenticatedEndpoint.class.isAssignableFrom(c);
        String input = authenticated ? "Omit<" + rawInput + ", \"sessionId\">" : rawInput;

        writeLater("    public static " + name + " = {");
        writeLater("        call:(input:" + input + "):Promise<" + output + "> => {");
        writeLater("            return Api.call<" + input + ", " + output + ">({");
        writeLater("                    method:\"" + endpoint.method() + "\",");
        writeLater("                    body:input,");
        writeLater("                    endpoint:\"" + name + "\"");
        writeLater("            });");
        writeLater("        }");
        writeLater("    }");
        writeLater("");
      }
    }

    write("import { " + toImport.stream().collect(Collectors.joining(",")) + " } from \"./pieroxy-mom\";");
    for (String s : later) write(s);
    write("}");

    writer.close();
  }

  /**
   * Mirrors {@link AbstractApiEndpoint#resolveInputType} (see its javadoc for why a plain
   * "read the type argument off the direct subclass" walk isn't enough once an intermediate
   * generic superclass — e.g. AbstractAuthenticatedEndpoint — is in the chain).
   */
  private static String getType(Class<?> leafClass, int argIndex) {
    Map<TypeVariable<?>, Type> bindings = new HashMap<>();
    Class<?> current = leafClass;
    Class<?> clazz = null;
    while (true) {
      Class<?> rawSuperclass = current.getSuperclass();
      if (rawSuperclass == null) throw new IllegalArgumentException("Could not resolve type argument " + argIndex + " for " + leafClass);
      Type genericSuperclass = current.getGenericSuperclass();
      Type[] actualArgs = (genericSuperclass instanceof ParameterizedType parameterizedType)
          ? parameterizedType.getActualTypeArguments() : new Type[0];

      if (rawSuperclass == AbstractApiEndpoint.class) {
        Type arg = actualArgs[argIndex];
        Type resolved = (arg instanceof TypeVariable<?>) ? bindings.get(arg) : arg;
        if (!(resolved instanceof Class<?>)) {
          throw new IllegalArgumentException("Could not resolve a concrete type argument " + argIndex + " for " + leafClass + " (got " + resolved + ")");
        }
        clazz = (Class<?>) resolved;
        break;
      }

      TypeVariable<?>[] params = rawSuperclass.getTypeParameters();
      for (int i = 0; i < params.length && i < actualArgs.length; i++) {
        Type arg = actualArgs[i];
        bindings.put(params[i], (arg instanceof TypeVariable<?>) ? bindings.getOrDefault(arg, arg) : arg);
      }
      current = rawSuperclass;
    }
    String res;
    if (clazz == Object.class) {
      res = "any";
    } else {
      res = clazz.getSimpleName();
      if (clazz.getAnnotation(TypeScriptType.class) == null)
        throw new RuntimeException(leafClass.getName() + " uses parameter that is not a TypeScriptType");
    }
    return res;
  }
}
