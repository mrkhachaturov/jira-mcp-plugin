package com.atlassian.mcp.plugin.tools.issues;

// Ported from mcp-atlassian v0.21.0 -- src/mcp_atlassian/servers/jira.py:search
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class SearchTool implements McpTool {

    private final JiraRestClient client;

    public SearchTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "search"; }

    @Override public String description() {
        return "Search for Jira issues using JQL (Jira Query Language). "
                + "Returns matching issues with their key fields.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "jql", Map.of(
                                "type", "string",
                                "description", "JQL query string (e.g., 'project = PROJ AND status = Open')"
                        ),
                        "maxResults", Map.of(
                                "type", "integer",
                                "description", "Maximum number of results to return (default: 50, max: 200)",
                                "default", 50
                        ),
                        "startAt", Map.of(
                                "type", "integer",
                                "description", "Index of the first result to return (for pagination)",
                                "default", 0
                        ),
                        "fields", Map.of(
                                "type", "string",
                                "description", "Comma-separated list of fields to return"
                        )
                ),
                "required", List.of("jql")
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String jql = (String) args.get("jql");
        if (jql == null || jql.isBlank()) {
            throw new McpToolException("'jql' parameter is required");
        }
        int maxResults = Math.min(getInt(args, "maxResults", 50), 200);
        int startAt = getInt(args, "startAt", 0);
        String fields = (String) args.getOrDefault("fields", "");

        String queryParams = "?jql=" + encode(jql)
                + "&maxResults=" + maxResults
                + "&startAt=" + startAt;
        if (fields != null && !fields.isBlank()) {
            queryParams += "&fields=" + encode(fields);
        }

        return client.get("/rest/api/2/search" + queryParams, authHeader);
    }

    private static int getInt(Map<String, Object> args, String key, int defaultVal) {
        Object val = args.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return defaultVal; }
        }
        return defaultVal;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
