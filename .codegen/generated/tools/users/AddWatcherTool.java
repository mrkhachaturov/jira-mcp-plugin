package com.atlassian.mcp.plugin.tools.users;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddWatcherTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public AddWatcherTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "add_watcher"; }

    @Override
    public String description() {
        return "Add a user as a watcher to a Jira issue.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123')"),
                        "user_identifier", Map.of("type", "string", "description", "User to add as watcher. For Jira Cloud, use the account ID. For Jira Server/DC, use the username.")
                ),
                "required", List.of("issue_key", "user_identifier")
        );
    }

    @Override public boolean isWriteTool() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        if (issueKey == null || issueKey.isBlank()) {
            throw new McpToolException("'issue_key' parameter is required");
        }
        String userIdentifier = (String) args.get("user_identifier");
        if (userIdentifier == null || userIdentifier.isBlank()) {
            throw new McpToolException("'user_identifier' parameter is required");
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("user_identifier", userIdentifier);
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            return client.post("/rest/api/2/issue/" + issueKey + "/watchers", jsonBody, authHeader);
        } catch (Exception e) {
            throw new McpToolException("Failed to serialize request: " + e.getMessage());
        }
    }
}
