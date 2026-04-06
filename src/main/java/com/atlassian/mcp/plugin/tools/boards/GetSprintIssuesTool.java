package com.atlassian.mcp.plugin.tools.boards;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class GetSprintIssuesTool implements McpTool {
    private final JiraRestClient client;

    public GetSprintIssuesTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_sprint_issues"; }

    @Override
    public String description() {
        return "Get jira issues from sprint.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "sprint_id", Map.of("type", "string", "description", "The id of sprint (e.g., '10001')"),
                        "fields", Map.of("type", "string", "description", "Comma-separated fields to return in the results. Use '*all' for all fields, or specify individual fields like 'summary,status,assignee,priority'", "default", "summary,status,assignee,reporter,priority,issuetype,created,updated,description,comment,labels,components,fixVersions,resolution,subtasks,issuelinks,attachment,parent"),
                        "start_at", Map.of("type", "integer", "description", "Starting index for pagination (0-based)", "default", 0),
                        "limit", Map.of("type", "integer", "description", "Maximum number of results (1-50)", "default", 10)
                ),
                "required", List.of("sprint_id")
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String requiredPluginKey() { return "com.atlassian.jira.plugins.jira-software-plugin"; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String sprintId = (String) args.get("sprint_id");
        if (sprintId == null || sprintId.isBlank()) {
            throw new McpToolException("'sprint_id' parameter is required");
        }
        String fields = (String) args.getOrDefault("fields", "summary,status,assignee,reporter,priority,issuetype,created,updated,description,comment,labels,components,fixVersions,resolution,subtasks,issuelinks,attachment,parent");
        int startAt = getInt(args, "start_at", 0);
        int limit = Math.min(getInt(args, "limit", 10), 50);

        StringBuilder query = new StringBuilder();
        query.append("?maxResults=").append(limit);
        query.append("&startAt=").append(startAt);
        if (fields != null && !fields.isBlank()) {
            query.append("&fields=").append(encode(fields));
        }

        return client.get("/rest/agile/1.0/sprint/" + sprintId + "/issue" + query, authHeader);
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
