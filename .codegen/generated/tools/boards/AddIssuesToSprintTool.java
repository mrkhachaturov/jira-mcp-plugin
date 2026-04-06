package com.atlassian.mcp.plugin.tools.boards;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddIssuesToSprintTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public AddIssuesToSprintTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "add_issues_to_sprint"; }

    @Override
    public String description() {
        return "Add issues to a Jira sprint.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "sprint_id", Map.of("type", "string", "description", "Sprint ID to add issues to"),
                        "issue_keys", Map.of("type", "string", "description", "Comma-separated issue keys (e.g., 'PROJ-1,PROJ-2')")
                ),
                "required", List.of("sprint_id", "issue_keys")
        );
    }

    @Override public boolean isWriteTool() { return true; }

    @Override
    public String requiredPluginKey() { return "com.atlassian.jira.plugins.jira-software-plugin"; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String sprintId = (String) args.get("sprint_id");
        if (sprintId == null || sprintId.isBlank()) {
            throw new McpToolException("'sprint_id' parameter is required");
        }
        String issueKeys = (String) args.get("issue_keys");
        if (issueKeys == null || issueKeys.isBlank()) {
            throw new McpToolException("'issue_keys' parameter is required");
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("issue_keys", issueKeys);
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            return client.post("/rest/agile/1.0/sprint/" + sprintId + "/issue", jsonBody, authHeader);
        } catch (Exception e) {
            throw new McpToolException("Failed to serialize request: " + e.getMessage());
        }
    }
}
