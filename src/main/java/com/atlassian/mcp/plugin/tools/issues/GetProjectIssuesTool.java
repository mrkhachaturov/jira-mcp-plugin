package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class GetProjectIssuesTool implements McpTool {
    private final JiraRestClient client;

    public GetProjectIssuesTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_project_issues"; }

    @Override
    public String description() {
        return "Get all issues for a specific Jira project.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "project_key", Map.of("type", "string", "description", "Jira project key (e.g., 'PROJ', 'ACV2')"),
                        "limit", Map.of("type", "integer", "description", "Maximum number of results (1-50)", "default", 10),
                        "start_at", Map.of("type", "integer", "description", "Starting index for pagination (0-based)", "default", 0)
                ),
                "required", List.of("project_key")
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String projectKey = (String) args.get("project_key");
        if (projectKey == null || projectKey.isBlank()) {
            throw new McpToolException("'project_key' parameter is required");
        }
        int limit = Math.min(getInt(args, "limit", 10), 50);
        int startAt = getInt(args, "start_at", 0);

        String jql = "project=" + encode(projectKey) + " ORDER BY created DESC";
        String query = "?jql=" + encode(jql) + "&maxResults=" + limit + "&startAt=" + startAt;
        return client.get("/rest/api/2/search" + query, authHeader);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static int getInt(Map<String, Object> args, String key, int defaultVal) {
        Object val = args.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return defaultVal; }
        }
        return defaultVal;
    }
}
