package com.atlassian.mcp.plugin.tools;

import com.atlassian.mcp.plugin.McpToolException;
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
}
