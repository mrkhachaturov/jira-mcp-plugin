package com.atlassian.mcp.plugin.tools;

import com.atlassian.mcp.plugin.McpToolException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Binds the raw MCP argument map onto a tool's argument record, applying declared defaults and
 * rejecting unknown keys, absent required parameters and values outside a declared enum. Nested
 * argument records are checked the same way, and are named by path in any complaint.
 */
public final class ToolArgsBinder {

  private static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
          .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
          .build();

  private ToolArgsBinder() {}

  public static <A> A bind(Class<A> argsType, Map<String, Object> raw) throws McpToolException {
    Map<String, Object> checked = check(argsType, raw, "");
    try {
      return MAPPER.convertValue(checked, argsType);
    } catch (IllegalArgumentException e) {
      throw new McpToolException(explain(argsType, e));
    }
  }

  private static Map<String, Object> check(Class<?> type, Map<String, Object> raw, String path)
      throws McpToolException {
    Map<String, Object> supplied = new LinkedHashMap<>();
    if (raw != null) {
      raw.forEach(
          (key, value) -> {
            if (value == null) return;
            if (value instanceof String text && text.isBlank()) return;
            supplied.put(key, value);
          });
    }

    for (RecordComponent component : type.getRecordComponents()) {
      ToolArg arg = component.getAnnotation(ToolArg.class);
      String name = ToolSchema.wireName(component);
      String qualified = path.isEmpty() ? name : path + "." + name;
      Object value = supplied.get(name);

      if (value == null) {
        if (arg.required()) throw new McpToolException("'" + qualified + "' parameter is required");
        if (!arg.defaultValue().isEmpty()) supplied.put(name, arg.defaultValue());
        continue;
      }
      if (arg.allowed().length > 0 && !List.of(arg.allowed()).contains(String.valueOf(value))) {
        throw new McpToolException(
            "'" + qualified + "' must be one of " + String.join(", ", arg.allowed()));
      }

      Class<?> nested = ToolSchema.nestedRecord(component);
      if (nested == null) continue;

      if (component.getType().isRecord()) {
        supplied.put(name, check(nested, asMap(value, qualified), qualified));
      } else if (value instanceof List<?> elements) {
        List<Object> checked = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
          String indexed = qualified + "[" + i + "]";
          checked.add(check(nested, asMap(elements.get(i), indexed), indexed));
        }
        supplied.put(name, checked);
      }
    }
    return supplied;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value, String path) throws McpToolException {
    if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
    throw new McpToolException("'" + path + "' must be an object");
  }

  private static String explain(Class<?> argsType, IllegalArgumentException e) {
    Throwable cause = e.getCause() == null ? e : e.getCause();
    if (cause instanceof UnrecognizedPropertyException unrecognized) {
      return "Unknown parameter '"
          + unrecognized.getPropertyName()
          + "'. Accepted parameters: "
          + String.join(", ", names(argsType));
    }
    return "Could not read arguments: " + cause.getMessage();
  }

  private static List<String> names(Class<?> argsType) {
    List<String> names = new ArrayList<>();
    for (RecordComponent component : argsType.getRecordComponents()) {
      names.add(ToolSchema.wireName(component));
    }
    return names;
  }
}
