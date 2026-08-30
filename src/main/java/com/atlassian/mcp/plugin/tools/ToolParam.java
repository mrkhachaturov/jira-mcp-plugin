package com.atlassian.mcp.plugin.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * One declared input parameter: its JSON Schema fragment and the coercion used to read it back out
 * of the client's arguments. Declaring it once is the point — the schema advertised in
 * {@code tools/list} and the value {@link ToolArgs} hands to the tool come from this same object,
 * so a parameter cannot be advertised and then silently ignored.
 */
public final class ToolParam<T> {

    private final String name;
    private final Map<String, Object> schema;
    private final boolean required;
    private final T defaultValue;
    private final Function<Object, T> coerce;

    private ToolParam(String name, Map<String, Object> schema, boolean required,
                      T defaultValue, Function<Object, T> coerce) {
        this.name = name;
        this.schema = schema;
        this.required = required;
        this.defaultValue = defaultValue;
        this.coerce = coerce;
    }

    public static ToolParam<String> string(String name, String description) {
        return new ToolParam<>(name, schema("string", description), false, null, ToolParam::asString);
    }

    public static ToolParam<Integer> integer(String name, String description) {
        return new ToolParam<>(name, schema("integer", description), false, null, ToolParam::asInt);
    }

    public static ToolParam<Boolean> bool(String name, String description) {
        return new ToolParam<>(name, schema("boolean", description), false, Boolean.FALSE,
                ToolParam::asBoolean);
    }

    /** For array/object parameters, whose schema is too specific to build from a factory. */
    public static ToolParam<Object> of(String name, Map<String, Object> schema) {
        return new ToolParam<>(name, new LinkedHashMap<>(schema), false, null, value -> value);
    }

    public ToolParam<T> required() {
        return new ToolParam<>(name, schema, true, defaultValue, coerce);
    }

    public ToolParam<T> withDefault(T value) {
        Map<String, Object> withDefault = new LinkedHashMap<>(schema);
        withDefault.put("default", value);
        return new ToolParam<>(name, withDefault, required, value, coerce);
    }

    public ToolParam<T> allowing(String... values) {
        Map<String, Object> withEnum = new LinkedHashMap<>(schema);
        withEnum.put("enum", List.of(values));
        return new ToolParam<>(name, withEnum, required, defaultValue, coerce);
    }

    public String name() {
        return name;
    }

    public boolean isRequired() {
        return required;
    }

    public Map<String, Object> schema() {
        return schema;
    }

    /** Coerces a raw argument value, falling back to the declared default when absent. */
    T read(Object raw) {
        if (raw == null) return defaultValue;
        T coerced = coerce.apply(raw);
        return coerced != null ? coerced : defaultValue;
    }

    private static Map<String, Object> schema(String type, String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        schema.put("description", description);
        return schema;
    }

    private static String asString(Object value) {
        if (value instanceof String s) return s.isBlank() ? null : s;
        return value == null ? null : String.valueOf(value);
    }

    private static Integer asInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.valueOf("true".equalsIgnoreCase(s.trim()));
        return null;
    }
}
