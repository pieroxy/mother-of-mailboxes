package net.pieroxy.imf.api;

import net.pieroxy.imf.api.metadata.AbstractApiEndpoint;
import net.pieroxy.imf.api.metadata.ApiEndpoint;
import net.pieroxy.imf.api.metadata.Endpoint;
import net.pieroxy.imf.api.metadata.TypeScriptType;
import net.pieroxy.imf.utils.reflection.GetAccessibleClasses;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
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
    List<Class<?>> allClasses = GetAccessibleClasses.getClasses("net.pieroxy.imf");
    for (Class<?> c : allClasses) {
      if (ApiEndpoint.class.isAssignableFrom(c) && c.isAnnotationPresent(Endpoint.class)) {
        System.out.println("Processing " + c.getName());
        Endpoint endpoint = c.getAnnotation(Endpoint.class);
        String input = getType(c, 0);
        String output = getType(c, 1);
        String name = c.getSimpleName().substring(0, c.getSimpleName().length() - "Api".length());
        if (!input.equals("any")) toImport.add(input);
        if (!output.equals("any")) toImport.add(output);

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

    write("import { " + toImport.stream().collect(Collectors.joining(",")) + " } from \"./pieroxy-imf\";");
    for (String s : later) write(s);
    write("}");

    writer.close();
  }

  private static String getType(Class<?> subClass, int i) {
    while (subClass.getSuperclass() != AbstractApiEndpoint.class) {
      subClass = subClass.getSuperclass();
      if (subClass == null) throw new IllegalArgumentException();
    }
    ParameterizedType parameterizedType = (ParameterizedType) subClass.getGenericSuperclass();
    Class<?> clazz = (Class<?>) parameterizedType.getActualTypeArguments()[i];
    String res;
    if (clazz == Object.class) {
      res = "any";
    } else {
      res = clazz.getSimpleName();
      if (clazz.getAnnotation(TypeScriptType.class) == null)
        throw new RuntimeException(subClass.getName() + " uses parameter that is not a TypeScriptType");
    }
    return res;
  }
}
