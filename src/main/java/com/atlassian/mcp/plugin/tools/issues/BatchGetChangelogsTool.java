package com.atlassian.mcp.plugin.tools.issues;

// Ported from mcp-atlassian v0.21.0 -- src/mcp_atlassian/servers/jira.py:batch_get_changelogs
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class BatchGetChangelogsTool implements McpTool {
    private final JiraRestClient client;
    public BatchGetChangelogsTool(JiraRestClient client) { this.client = client; }

    @Override public String name() { return "batch_get_changelogs"; }
    @Override public String description() { return "Get the changelog (history of changes) for a Jira issue."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123')"),
                        "maxResults", Map.of("type", "integer", "description", "Max results", "default", 50),
                        "startAt", Map.of("type", "integer", "description", "Pagination offset", "default", 0)),
                "required", List.of("issue_key"));
    }
    @Override public boolean isWriteTool() { return false; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        if (issueKey == null) throw new McpToolException("issue_key is required");
        return client.get("/rest/api/2/issue/" + issueKey + "/changelog", authHeader);
    }
}
