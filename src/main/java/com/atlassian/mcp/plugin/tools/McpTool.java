package com.atlassian.mcp.plugin.tools;

import com.atlassian.mcp.plugin.McpToolException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import java.util.Map;

public interface McpTool {

  /** Tool name (snake_case). */
  String name();

  /** Human-readable description. */
  String description();

  /** JSON Schema for the tool's input parameters. */
  Map<String, Object> inputSchema();

  /** True if this tool modifies data (create, update, delete, transition). */
  boolean isWriteTool();

  /**
   * Optional: plugin key required for this tool to work. Return null if no specific plugin is
   * required (works on all Jira instances).
   */
  default String requiredPluginKey() {
    return null;
  }

  /**
   * Execute the tool with the given arguments.
   *
   * @param args parsed JSON arguments from the MCP client
   * @param authHeader the user's Authorization header (forwarded to Jira REST API)
   * @return JSON string result
   */
  String execute(Map<String, Object> args, String authHeader) throws McpToolException;

  /**
   * Whether this tool supports streaming execution with progress notifications. Override and return
   * true in batch tools that process multiple items.
   */
  default boolean supportsProgress() {
    return false;
  }

  /**
   * Execute the tool with progress reporting. Called instead of execute() when the client sends a
   * progressToken and supportsProgress() is true.
   *
   * @param args parsed JSON arguments
   * @param authHeader Authorization header
   * @param progress callback to report progress during execution
   * @return JSON string result (same as execute)
   */
  default String executeWithProgress(
      Map<String, Object> args, String authHeader, ProgressCallback progress)
      throws McpToolException {
    return execute(args, authHeader);
  }

  /**
   * Whether this tool performs destructive operations (delete, remove). Used for MCP tool
   * annotations (destructiveHint).
   */
  default boolean isDestructiveTool() {
    return false;
  }

  /**
   * Tool annotation: human-readable display name (null = no override).
   *
   * <p>Per MCP spec {@code 2025-11-25/server/tools.mdx} (SEP-973), the optional {@code title} is
   * surfaced to users when clients render tool lists. Tools may override to expose a friendly label
   * distinct from the snake_case {@link #name()}.
   */
  default String title() {
    return null;
  }

  /**
   * Tool annotation: idempotent &rArr; calling repeatedly with the same args has no additional
   * effect beyond the first call.
   *
   * <p>Default: read-only tools are idempotent. Write tools may override and return {@code true} if
   * their effect is keyed by resource identity (e.g. {@code update_issue} on a stable issue key
   * reaches the same end state regardless of how many times it runs).
   */
  default Boolean idempotentHint() {
    return !isWriteTool();
  }

  /**
   * Tool annotation: open-world &rArr; the tool interacts with external systems (vs a closed
   * sandbox). All tools in this plugin call the Jira REST API, so the default is {@code true}.
   */
  default Boolean openWorldHint() {
    return Boolean.TRUE;
  }

  /** Callback for reporting progress during streaming execution. */
  @FunctionalInterface
  interface ProgressCallback {
    /**
     * @param current items processed so far
     * @param total total items (or -1 if unknown)
     * @param message human-readable status message
     */
    void report(int current, int total, String message);
  }

  /**
   * The {@code ui://} resource URI that should be advertised via the tool's {@code
   * _meta.ui.resourceUri} (and {@code openai/widgetResource} for ChatGPT) to link this tool to an
   * MCP App widget. Return {@code null} (default) if the tool has no UI binding.
   */
  default String uiResourceUri() {
    return null;
  }

  /**
   * Optional MCP Apps tool visibility per {@code 2026-01-26} spec §Tool Metadata (L324-344).
   * Values: {@code "model"} (callable by the agent), {@code "app"} (callable from a widget only).
   * Return {@code null} (default) to omit the field — host treats this as {@code ["model", "app"]}.
   *
   * <p>Use cases:
   *
   * <ul>
   *   <li>App-only helper tools (e.g. an internal "refresh" endpoint a widget calls but the model
   *       should never invoke) → return {@code ["app"]}.
   *   <li>Tools we never want exposed to widgets → return {@code ["model"]}.
   * </ul>
   */
  default java.util.List<String> uiVisibility() {
    return null;
  }

  /**
   * Optional per-tool icon (data URI or http URL). Null = client falls back to the server-level
   * icon advertised in {@code Implementation.icons}. Per MCP spec {@code 2025-11-25} (SEP-973), the
   * wire shape is {@code icons: [{ src, mimeType, sizes }]}; this method returns just the {@code
   * src} string and the adapter infers the rest.
   */
  default String iconUri() {
    return null;
  }

  /**
   * Optional outputSchema describing the {@code structuredContent} payload the tool emits. Null =
   * no schema advertised (clients can't validate structuredContent shape). Per MCP spec {@code
   * 2025-11-25} (SEP-1330).
   */
  default Map<String, Object> outputSchema() {
    return null;
  }

  /**
   * Optional structuredContent payload for the tool result. Called by the SDK adapter after a
   * successful {@code execute()} for tools that want to attach a typed JSON object (e.g. for MCP
   * Apps widget rendering). Return {@code null} (default) to skip structuredContent emission.
   *
   * @param args the original tool arguments
   * @param executeResult the string returned by {@code execute()}
   * @param jiraUsername the resolved Jira username (may be {@code null})
   * @param jiraUserDisplay the resolved Jira display name (may be {@code null})
   */
  default ObjectNode structuredContent(
      Map<String, Object> args, String executeResult, String jiraUsername, String jiraUserDisplay) {
    return null;
  }

  /**
   * SDK-aware progress-capable execution. Default implementation falls back to the legacy {@link
   * #executeWithProgress(Map, String, ProgressCallback)} (and ultimately {@link #execute(Map,
   * String)}) when no exchange/progress token plumbing is needed. Batch tools override this to call
   * {@code exchange.progressNotification(new ProgressNotification(...))} using the supplied {@code
   * progressToken}.
   *
   * @param args parsed arguments
   * @param authHeader user's Authorization header
   * @param exchange the SDK server exchange; never {@code null}
   * @param progressToken progress token from {@code params._meta.progressToken}, or {@code null} if
   *     the client did not request progress
   */
  default String executeWithSdkProgress(
      Map<String, Object> args,
      String authHeader,
      McpSyncServerExchange exchange,
      Object progressToken)
      throws McpToolException {
    return execute(args, authHeader);
  }
}
