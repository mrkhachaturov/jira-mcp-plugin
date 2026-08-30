package com.atlassian.mcp.plugin.tools;

import com.atlassian.mcp.plugin.McpToolException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Typed view over the raw MCP arguments. A value can only be read through the {@link ToolParam}
 * object that declared it, so a tool cannot reach for a key it never advertised. Reads are
 * recorded, which is what lets {@code DeclarativeToolContractTest} prove the converse — that every
 * advertised parameter is actually consumed.
 */
public final class ToolArgs {

  private final Map<String, ToolParam<?>> declared = new LinkedHashMap<>();
  private final Map<String, Object> raw;
  private final Set<String> readParams = new LinkedHashSet<>();

  public ToolArgs(Collection<ToolParam<?>> params, Map<String, Object> raw) {
    for (ToolParam<?> param : params) {
      declared.put(param.name(), param);
    }
    this.raw = raw == null ? Map.of() : raw;
  }

  public <T> T get(ToolParam<T> param) {
    // Identity, not name: two params with the same name from different tools would otherwise
    // read each other's coercion and defaults.
    if (declared.get(param.name()) != param) {
      throw new IllegalArgumentException(
          "Parameter '" + param.name() + "' is read but not declared in params()");
    }
    readParams.add(param.name());
    return param.read(raw.get(param.name()));
  }

  public <T> T require(ToolParam<T> param) throws McpToolException {
    T value = get(param);
    if (value == null) {
      throw new McpToolException("'" + param.name() + "' parameter is required");
    }
    return value;
  }

  public Set<String> readParams() {
    return Set.copyOf(readParams);
  }
}
