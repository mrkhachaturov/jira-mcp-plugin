package com.atlassian.mcp.plugin.tools.issues;

// Ported from mcp-atlassian v0.21.0 -- src/mcp_atlassian/servers/jira.py:delete_issue
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class DeleteIssueTool implements McpTool {
    private final JiraRestClient client;
    public DeleteIssueTool(JiraRestClient client) { this.client = client; }

    @Override public String name() { return "delete_issue"; }
    @Override public String description() { return "Delete a Jira issue."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123')")),
                "required", List.of("issue_key"));
    }
    @Override public boolean isWriteTool() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        if (issueKey == null) throw new McpToolException("issue_key is required");
        return client.delete("/rest/api/2/issue/" + issueKey, authHeader);
    }
}
