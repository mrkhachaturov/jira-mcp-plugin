package com.atlassian.mcp.plugin.tools;

import com.atlassian.mcp.plugin.McpToolException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import java.util.Map;

public interface McpTool {

    /** Tool name matching upstream mcp-atlassian. */
    String name();

    /** Human-readable description. */
    String description();

    /** JSON Schema for the tool's input parameters. */
    Map<String, Object> inputSchema();

    /** True if this tool modifies data (create, update, delete, transition). */
    boolean isWriteTool();

    /**
     * Optional: plugin key required for this tool to work.
     * Return null if no specific plugin is required (works on all Jira instances).
     */
    default String requiredPluginKey() {
        return null;
    }

    /**
     * Execute the tool with the given arguments.
     * @param args parsed JSON arguments from the MCP client
     * @param authHeader the user's Authorization header (forwarded to Jira REST API)
     * @return JSON string result
     */
    String execute(Map<String, Object> args, String authHeader) throws McpToolException;

    /**
     * Whether this tool supports streaming execution with progress notifications.
     * Override and return true in batch tools that process multiple items.
     */
    default boolean supportsProgress() {
        return false;
    }

    /**
     * Execute the tool with progress reporting. Called instead of execute()
     * when the client sends a progressToken and supportsProgress() is true.
     *
     * @param args parsed JSON arguments
     * @param authHeader Authorization header
     * @param progress callback to report progress during execution
     * @return JSON string result (same as execute)
     */
    default String executeWithProgress(Map<String, Object> args, String authHeader,
                                       ProgressCallback progress) throws McpToolException {
        return execute(args, authHeader);
    }

    /**
     * Whether this tool performs destructive operations (delete, remove).
     * Used for MCP tool annotations (destructiveHint).
     */
    default boolean isDestructiveTool() {
        return false;
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
     * The {@code ui://} resource URI that should be advertised via the tool's
     * {@code _meta.ui.resourceUri} (and {@code openai/widgetResource} for ChatGPT)
     * to link this tool to an MCP App widget. Return {@code null} (default) if
     * the tool has no UI binding.
     */
    default String uiResourceUri() {
        return null;
    }

    /**
     * Optional structuredContent payload for the tool result. Called by the SDK
     * adapter after a successful {@code execute()} for tools that want to attach
     * a typed JSON object (e.g. for MCP Apps widget rendering). Return
     * {@code null} (default) to skip structuredContent emission.
     *
     * @param args         the original tool arguments
     * @param executeResult the string returned by {@code execute()}
     * @param jiraUsername the resolved Jira username (may be {@code null})
     * @param jiraUserDisplay the resolved Jira display name (may be {@code null})
     */
    default ObjectNode structuredContent(Map<String, Object> args,
                                         String executeResult,
                                         String jiraUsername,
                                         String jiraUserDisplay) {
        return null;
    }

    /**
     * SDK-aware progress-capable execution. Default implementation falls back to
     * the legacy {@link #executeWithProgress(Map, String, ProgressCallback)}
     * (and ultimately {@link #execute(Map, String)}) when no exchange/progress
     * token plumbing is needed. Batch tools override this to call
     * {@code exchange.progressNotification(new ProgressNotification(...))} using
     * the supplied {@code progressToken}.
     *
     * @param args          parsed arguments
     * @param authHeader    user's Authorization header
     * @param exchange      the SDK server exchange; never {@code null}
     * @param progressToken progress token from {@code params._meta.progressToken},
     *                      or {@code null} if the client did not request progress
     */
    default String executeWithSdkProgress(Map<String, Object> args,
                                          String authHeader,
                                          McpSyncServerExchange exchange,
                                          Object progressToken) throws McpToolException {
        return execute(args, authHeader);
    }
}
