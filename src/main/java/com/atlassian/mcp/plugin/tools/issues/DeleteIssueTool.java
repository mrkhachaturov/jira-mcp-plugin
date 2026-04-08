package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.util.List;
import java.util.Map;

public class DeleteIssueTool implements McpTool {
    private final JiraRestClient client;

    public DeleteIssueTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "delete_issue"; }

    @Override
    public String description() {
        return "Delete an existing Jira issue.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')")
                ),
                "required", List.of("issue_key")
        );
    }

    @Override public boolean isWriteTool() { return true; }

    @Override public boolean isDestructiveTool() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        if (issueKey == null || issueKey.isBlank()) {
            throw new McpToolException("'issue_key' parameter is required");
        }

        return client.delete("/rest/api/2/issue/" + issueKey, authHeader);
    }
}
