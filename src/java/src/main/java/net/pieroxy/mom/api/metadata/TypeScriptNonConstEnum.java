package net.pieroxy.mom.api.metadata;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code @TypeScriptType} enum to be emitted as a regular TS {@code enum} instead of the
 * default {@code const enum} (see the typescript-generator-maven-plugin's
 * {@code nonConstEnumAnnotations} config in pom.xml). A {@code const enum} is erased at compile
 * time — every reference gets inlined to its literal value, so there's no runtime object to
 * iterate. Use this on an enum the webapp needs to enumerate at runtime (e.g. to populate a
 * dropdown via {@code Object.values(...)}) rather than just compare against a single value.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TypeScriptNonConstEnum {
}
