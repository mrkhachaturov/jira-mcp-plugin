package com.atlassian.mcp.plugin.tools.projects;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.util.List;
import java.util.Map;

public class GetAllProjectsTool implements McpTool {
    private final JiraRestClient client;

    public GetAllProjectsTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_all_projects"; }

    @Override
    public String description() {
        return "Get all Jira projects accessible to the current user.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "include_archived", Map.of("type", "boolean", "description", "Whether to include archived projects in the results", "default", false)
                ),
                "required", List.of()
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        boolean includeArchived = getBoolean(args, "include_archived", false);

        return client.get("/rest/api/2/project", authHeader);
    }

    private static boolean getBoolean(Map<String, Object> args, String key, boolean defaultVal) {
        Object val = args.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return "true".equalsIgnoreCase(s);
        return defaultVal;
    }
}
