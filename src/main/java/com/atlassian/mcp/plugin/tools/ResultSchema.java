package com.atlassian.mcp.plugin.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the JSON Schema a tool advertises for its {@code structuredContent} from the record that
 * produces it. Property names are the ones Jackson writes — the record component, or the
 * {@code @JsonProperty} that overrides it.
 */
public final class ResultSchema {

  private ResultSchema() {}

  public static Map<String, Object> of(Class<?> resultType) {
    return of(resultType, new LinkedHashSet<>());
  }

  private static Map<String, Object> of(Class<?> resultType, Set<Class<?>> enclosing) {
    if (!resultType.isRecord()) {
      throw new IllegalArgumentException(resultType.getName() + " must be a record");
    }
    if (!enclosing.add(resultType)) {
      throw new IllegalStateException("Recursive result record: " + resultType.getName());
    }

    Map<String, Object> properties = new LinkedHashMap<>();
    for (RecordComponent component : resultType.getRecordComponents()) {
      properties.put(name(component), typeOf(component.getGenericType(), enclosing));
    }

    enclosing.remove(resultType);

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", Collections.unmodifiableMap(properties));
    return Collections.unmodifiableMap(schema);
  }

  private static String name(RecordComponent component) {
    JsonProperty renamed = component.getAnnotation(JsonProperty.class);
    if (renamed == null) {
      try {
        renamed =
            component
                .getDeclaringRecord()
                .getDeclaredField(component.getName())
                .getAnnotation(JsonProperty.class);
      } catch (NoSuchFieldException ignored) {
        renamed = null;
      }
    }
    if (renamed == null) {
      renamed = component.getAccessor().getAnnotation(JsonProperty.class);
    }
    return renamed == null || renamed.value().isEmpty() ? component.getName() : renamed.value();
  }

  private static Map<String, Object> typeOf(Type type, Set<Class<?>> enclosing) {
    if (type instanceof ParameterizedType parameterized) {
      Class<?> raw = (Class<?>) parameterized.getRawType();
      if (List.class.isAssignableFrom(raw)) {
        return Map.of(
            "type", "array", "items", typeOf(parameterized.getActualTypeArguments()[0], enclosing));
      }
      throw new IllegalStateException("Unsupported result type: " + type);
    }

    Class<?> raw = (Class<?>) type;
    if (raw.isRecord()) return of(raw, enclosing);
    if (raw == String.class) return Map.of("type", "string");
    if (raw == boolean.class || raw == Boolean.class) return Map.of("type", "boolean");
    if (raw == int.class || raw == Integer.class || raw == long.class || raw == Long.class) {
      return Map.of("type", "integer");
    }
    if (raw == double.class || raw == Double.class) return Map.of("type", "number");
    throw new IllegalStateException("Unsupported result type: " + raw.getName());
  }
}
