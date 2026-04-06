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
}
