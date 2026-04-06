package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.util.List;
import java.util.Map;

public class BatchGetChangelogsTool implements McpTool {
    private final JiraRestClient client;

    public BatchGetChangelogsTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "batch_get_changelogs"; }

    @Override
    public String description() {
        return "Get changelogs for multiple Jira issues (Cloud only).";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_ids_or_keys", Map.of("type", "string", "description", "Comma-separated list of Jira issue IDs or keys (e.g. 'PROJ-123,PROJ-124')"),
                        "fields", Map.of("type", "string", "description", "(Optional) Comma-separated list of fields to filter changelogs by (e.g. 'status,assignee'). Default to None for all fields."),
                        "limit", Map.of("type", "integer", "description", "Maximum number of changelogs to return in result for each issue. Default to -1 for all changelogs. Notice that it only limits the results in the response, the function will still fetch all the data.", "default", -1)
                ),
                "required", List.of("issue_ids_or_keys")
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueIdsOrKeys = (String) args.get("issue_ids_or_keys");
        if (issueIdsOrKeys == null || issueIdsOrKeys.isBlank()) {
            throw new McpToolException("'issue_ids_or_keys' parameter is required");
        }
        String fields = (String) args.get("fields");
        int limit = getInt(args, "limit", -1);

        return client.get("/rest/api/2/issue/" + issueKey + "/changelog", authHeader);
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
