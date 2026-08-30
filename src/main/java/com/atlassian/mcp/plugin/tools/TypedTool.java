package com.atlassian.mcp.plugin.tools;

import com.atlassian.mcp.plugin.McpToolException;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import java.util.Map;

/**
 * Base for tools whose parameters are declared as a record. The advertised schema is derived from
 * that record, and the bound record is what {@link #run} receives. The declaration is validated
 * when the tool is constructed.
 *
 * <p>{@link #run} is the only entry point a subclass implements; whether the caller asked for
 * progress notifications is settled by the {@link McpContext} it is handed.
 *
 * @param <A> the argument record
 */
public abstract class TypedTool<A> implements McpTool {

  private final Class<A> argsType;
  private final Map<String, Object> inputSchema;

  protected TypedTool(Class<A> argsType) {
    this.argsType = argsType;
    this.inputSchema = ToolSchema.of(argsType);
  }

  protected abstract String run(A args, McpContext context) throws McpToolException;

  @Override
  public final Map<String, Object> inputSchema() {
    return inputSchema;
  }

  @Override
  public final String execute(Map<String, Object> args, String authHeader) throws McpToolException {
    return run(ToolArgsBinder.bind(argsType, args), McpContext.of(authHeader));
  }

  @Override
  public final String executeWithProgress(
      Map<String, Object> args, String authHeader, ProgressCallback progress)
      throws McpToolException {
    return run(ToolArgsBinder.bind(argsType, args), McpContext.of(authHeader, progress));
  }

  @Override
  public final String executeWithSdkProgress(
      Map<String, Object> args,
      String authHeader,
      McpSyncServerExchange exchange,
      Object progressToken)
      throws McpToolException {
    return run(
        ToolArgsBinder.bind(argsType, args),
        McpContext.of(authHeader, BatchProgressBridge.bridge(exchange, progressToken)));
  }
}
