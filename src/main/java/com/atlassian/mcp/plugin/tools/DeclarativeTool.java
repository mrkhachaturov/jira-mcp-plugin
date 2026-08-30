package com.atlassian.mcp.plugin.tools;

import com.atlassian.mcp.plugin.McpToolException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base for tools whose input schema is generated from {@link #params()} rather than written by
 * hand. {@link #inputSchema()} and {@link #execute} are final: a subclass has no way to advertise
 * a parameter the schema does not contain, or to reach past {@link ToolArgs} for one it never
 * declared. That is the whole point — hand-written schemas drifted from the code that read them,
 * leaving parameters that were advertised, parsed and then dropped on the floor.
 */
public abstract class DeclarativeTool implements McpTool {

  public abstract List<ToolParam<?>> params();

  /** Public so the contract test can drive it with its own {@link ToolArgs} and inspect the reads. */
  public abstract String run(ToolArgs args, String authHeader) throws McpToolException;

  @Override
  public final Map<String, Object> inputSchema() {
    Map<String, Object> properties = new LinkedHashMap<>();
    List<String> required = new java.util.ArrayList<>();
    for (ToolParam<?> param : params()) {
      properties.put(param.name(), param.schema());
      if (param.isRequired()) required.add(param.name());
    }

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", properties);
    schema.put("required", List.copyOf(required));
    return schema;
  }

  @Override
  public final String execute(Map<String, Object> args, String authHeader) throws McpToolException {
    return run(new ToolArgs(params(), args), authHeader);
  }
}
