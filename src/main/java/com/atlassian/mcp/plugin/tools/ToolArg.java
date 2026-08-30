package com.atlassian.mcp.plugin.tools;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Describes one tool parameter, applied to a record component. The component supplies the wire name
 * (camelCase becomes snake_case) and the JSON Schema type; this annotation supplies what the Java
 * type cannot express.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface ToolArg {

  /** Description shown to the model in {@code tools/list}. */
  String value();

  boolean required() default false;

  /**
   * Applied when the caller omits the parameter. Written as it would arrive on the wire and bound
   * through the same coercion, so {@code "10"} is a valid default for an {@code int} component.
   */
  String defaultValue() default "";

  /** Restricts the parameter to these values, advertised as JSON Schema {@code enum}. */
  String[] allowed() default {};
}
