package com.atlassian.mcp.plugin.tools.boards;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class GetSprintIssuesTool implements McpTool {
    private final JiraRestClient client;
    public GetSprintIssuesTool(JiraRestClient client) { this.client = client; }

    @Override public String name() { return "get_sprint_issues"; }
    @Override public String description() { return "Get issues in a specific sprint."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
                "properties", Map.of(
                        "sprint_id", Map.of("type", "integer", "description", "Sprint ID"),
                        "maxResults", Map.of("type", "integer", "default", 50)),
                "required", List.of("sprint_id"));
    }
    @Override public boolean isWriteTool() { return false; }
    @Override public String requiredPluginKey() { return "com.atlassian.jira.plugins.jira-software-plugin"; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        Object sprintId = args.get("sprint_id");
        if (sprintId == null) throw new McpToolException("sprint_id is required");
        return client.get("/rest/agile/1.0/sprint/" + sprintId + "/issue?maxResults=50", authHeader);
    }
}
