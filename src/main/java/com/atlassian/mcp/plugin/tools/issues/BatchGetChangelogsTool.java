package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.util.ArrayList;
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
        return "Get changelogs for multiple Jira issues. Retrieves the change history for each issue showing field changes, status transitions, and who made each change.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_ids_or_keys", Map.of("type", "string", "description", "Comma-separated list of Jira issue IDs or keys (e.g. 'PROJ-123,PROJ-124')"),
                        "fields", Map.of("type", "string", "description", "(Optional) Comma-separated list of fields to filter changelogs by (e.g. 'status,assignee'). Default to None for all fields."),
                        "limit", Map.of("type", "integer", "description", "Maximum number of changelogs to return in result for each issue. Default to -1 for all changelogs.", "default", -1)
                ),
                "required", List.of("issue_ids_or_keys")
        );
    }

    @Override public boolean isWriteTool() { return false; }
    @Override public boolean supportsProgress() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        return executeWithProgress(args, authHeader, (current, total, message) -> {});
    }

    @Override
    public String executeWithProgress(Map<String, Object> args, String authHeader,
                                      ProgressCallback progress) throws McpToolException {
        String issueIdsOrKeys = (String) args.get("issue_ids_or_keys");
        if (issueIdsOrKeys == null || issueIdsOrKeys.isBlank()) {
            throw new McpToolException("'issue_ids_or_keys' parameter is required");
        }

        String[] keys = issueIdsOrKeys.split(",");
        List<String> trimmedKeys = new ArrayList<>();
        for (String k : keys) {
            String t = k.trim();
            if (!t.isEmpty()) trimmedKeys.add(t);
        }

        int total = trimmedKeys.size();
        List<String> results = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < total; i++) {
            String key = trimmedKeys.get(i);
            progress.report(i, total, "Fetching changelog for " + key + " (" + (i + 1) + "/" + total + ")");

            try {
                // Use expand=changelog which works on both Cloud and DC
                // (the /changelog endpoint is Cloud-only)
                String changelog = client.get("/rest/api/2/issue/" + key + "?expand=changelog&fields=key,summary", authHeader);
                results.add("\"" + key + "\":" + changelog);
            } catch (Exception e) {
                errors.add("\"" + key + "\":{\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}");
            }
        }

        progress.report(total, total, "Completed: " + results.size() + " fetched, " + errors.size() + " errors");

        StringBuilder sb = new StringBuilder("{\"changelogs\":{");
        sb.append(String.join(",", results));
        sb.append("},\"fetched\":").append(results.size());
        sb.append(",\"errors\":").append(errors.size());
        if (!errors.isEmpty()) {
            sb.append(",\"failed\":{").append(String.join(",", errors)).append("}");
        }
        sb.append("}");
        return sb.toString();
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
