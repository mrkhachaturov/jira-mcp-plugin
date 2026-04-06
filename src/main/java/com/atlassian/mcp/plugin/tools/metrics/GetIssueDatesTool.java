package com.atlassian.mcp.plugin.tools.metrics;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class GetIssueDatesTool implements McpTool {
    private final JiraRestClient client;

    public GetIssueDatesTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_issue_dates"; }

    @Override
    public String description() {
        return "Get date information and status transition history for a Jira issue. Returns dates (created, updated, due date, resolution date) and optionally status change history with time tracking for workflow analysis.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')"),
                        "include_status_changes", Map.of("type", "boolean", "description", "Include status change history with timestamps and durations", "default", true),
                        "include_status_summary", Map.of("type", "boolean", "description", "Include aggregated time spent in each status", "default", true)
                ),
                "required", List.of("issue_key")
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        if (issueKey == null || issueKey.isBlank()) {
            throw new McpToolException("'issue_key' parameter is required");
        }
        boolean includeStatusChanges = getBoolean(args, "include_status_changes", true);
        boolean includeStatusSummary = getBoolean(args, "include_status_summary", true);

        String fields = "created,updated,duedate,resolutiondate";
        String expand = includeStatusChanges ? "changelog" : "";

        StringBuilder query = new StringBuilder("?fields=").append(encode(fields));
        if (!expand.isEmpty()) {
            query.append("&expand=").append(expand);
        }

        return client.get("/rest/api/2/issue/" + issueKey + query, authHeader);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static boolean getBoolean(Map<String, Object> args, String key, boolean defaultVal) {
        Object val = args.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return "true".equalsIgnoreCase(s);
        return defaultVal;
    }
}
