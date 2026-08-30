package com.atlassian.mcp.plugin.tools;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the JSON Schema a tool advertises from the record its {@code run} method receives. Wire
 * names come from Jackson's snake-case strategy, the same one the binder resolves against. A
 * component whose type is itself a record is described as a nested object schema.
 */
public final class ToolSchema {

  private static final PropertyNamingStrategies.SnakeCaseStrategy NAMING =
      new PropertyNamingStrategies.SnakeCaseStrategy();

  private ToolSchema() {}

  public static String wireName(RecordComponent component) {
    return NAMING.translate(component.getName());
  }

  /**
   * The record type a component holds directly or as list elements, or null if it holds neither.
   */
  static Class<?> nestedRecord(RecordComponent component) {
    if (component.getType().isRecord()) return component.getType();
    if (component.getGenericType() instanceof ParameterizedType parameterized
        && List.class.isAssignableFrom((Class<?>) parameterized.getRawType())
        && parameterized.getActualTypeArguments()[0] instanceof Class<?> element
        && element.isRecord()) {
      return element;
    }
    return null;
  }

  public static Map<String, Object> of(Class<?> argsType) {
    return of(argsType, new LinkedHashSet<>());
  }

  private static Map<String, Object> of(Class<?> argsType, Set<Class<?>> enclosing) {
    if (!argsType.isRecord()) {
      throw new IllegalArgumentException(argsType.getName() + " must be a record");
    }
    if (!enclosing.add(argsType)) {
      throw new IllegalStateException("Recursive parameter record: " + argsType.getName());
    }

    Map<String, Object> properties = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();

    for (RecordComponent component : argsType.getRecordComponents()) {
      ToolArg arg = component.getAnnotation(ToolArg.class);
      if (arg == null) {
        throw new IllegalStateException(
            argsType.getSimpleName() + "." + component.getName() + " is missing @ToolArg");
      }
      if (component.getType().isPrimitive() && arg.defaultValue().isEmpty() && !arg.required()) {
        throw new IllegalStateException(
            argsType.getSimpleName()
                + "."
                + component.getName()
                + " is primitive, so it must be required() or carry a defaultValue()");
      }
      if (arg.allowed().length > 0 && component.getType() != String.class) {
        throw new IllegalStateException(
            argsType.getSimpleName()
                + "."
                + component.getName()
                + " declares allowed() but is not a String; the binder compares whole values, so"
                + " an enum over list elements is not expressible");
      }

      String name = wireName(component);
      properties.put(name, property(component, arg, enclosing));
      if (arg.required()) required.add(name);
    }

    enclosing.remove(argsType);

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", Collections.unmodifiableMap(properties));
    schema.put("required", List.copyOf(required));
    schema.put("additionalProperties", false);
    return Collections.unmodifiableMap(schema);
  }

  private static Map<String, Object> property(
      RecordComponent component, ToolArg arg, Set<Class<?>> enclosing) {
    Map<String, Object> property =
        new LinkedHashMap<>(typeOf(component.getGenericType(), enclosing));
    property.put("description", arg.value());
    if (arg.allowed().length > 0) property.put("enum", List.of(arg.allowed()));
    if (!arg.defaultValue().isEmpty()) {
      property.put("default", typedDefault(component.getType(), arg.defaultValue()));
    }
    return property;
  }

  private static Map<String, Object> typeOf(Type type, Set<Class<?>> enclosing) {
    if (type instanceof ParameterizedType parameterized) {
      Class<?> raw = (Class<?>) parameterized.getRawType();
      if (List.class.isAssignableFrom(raw)) {
        return Map.of(
            "type", "array", "items", typeOf(parameterized.getActualTypeArguments()[0], enclosing));
      }
      if (Map.class.isAssignableFrom(raw)) {
        return Map.of("type", "object");
      }
      throw new IllegalStateException("Unsupported parameter type: " + type);
    }

    Class<?> raw = (Class<?>) type;
    if (raw.isRecord()) return of(raw, enclosing);
    if (raw == String.class) return Map.of("type", "string");
    if (raw == boolean.class || raw == Boolean.class) return Map.of("type", "boolean");
    if (raw == int.class || raw == Integer.class || raw == long.class || raw == Long.class) {
      return Map.of("type", "integer");
    }
    if (raw == double.class || raw == Double.class) return Map.of("type", "number");
    throw new IllegalStateException("Unsupported parameter type: " + raw.getName());
  }

  private static Object typedDefault(Class<?> type, String raw) {
    if (type == boolean.class || type == Boolean.class) return Boolean.valueOf(raw);
    if (type == int.class || type == Integer.class) return Integer.valueOf(raw);
    if (type == long.class || type == Long.class) return Long.valueOf(raw);
    if (type == double.class || type == Double.class) return Double.valueOf(raw);
    return raw;
  }
}
