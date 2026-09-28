package net.pieroxy.mom.api;

import net.pieroxy.mom.api.metadata.AbstractApiEndpoint;
import net.pieroxy.mom.api.metadata.AbstractAuthenticatedEndpoint;
import net.pieroxy.mom.api.metadata.ApiEndpoint;
import net.pieroxy.mom.api.metadata.Endpoint;
import net.pieroxy.mom.api.metadata.TypeScriptType;
import net.pieroxy.mom.utils.reflection.GenericTypeArgumentResolver;
import net.pieroxy.mom.utils.reflection.GetAccessibleClasses;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/** Run by the {@code generate-api-ts-stubs} step in {@code pom.xml} (process-classes phase); writes the .ts path passed as args[0]. */
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

  private static String getType(Class<?> leafClass, int argIndex) {
    Class<?> clazz = GenericTypeArgumentResolver.resolve(leafClass, AbstractApiEndpoint.class, argIndex);
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
