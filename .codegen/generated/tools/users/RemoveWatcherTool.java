package com.atlassian.mcp.plugin.tools.users;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.util.List;
import java.util.Map;

public class RemoveWatcherTool implements McpTool {
    private final JiraRestClient client;

    public RemoveWatcherTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "remove_watcher"; }

    @Override
    public String description() {
        return "Remove a user from watching a Jira issue.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123')"),
                        "username", Map.of("type", "string", "description", "Username to remove (for Jira Server/DC)."),
                        "account_id", Map.of("type", "string", "description", "Account ID to remove (for Jira Cloud).")
                ),
                "required", List.of("issue_key")
        );
    }

    @Override public boolean isWriteTool() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        if (issueKey == null || issueKey.isBlank()) {
            throw new McpToolException("'issue_key' parameter is required");
        }
        String username = (String) args.get("username");
        String accountId = (String) args.get("account_id");

        StringBuilder query = new StringBuilder();
        String sep = "?";
        if (username != null && !username.isBlank()) {
            query.append(sep).append("username=").append(encode(username));
            sep = "&";
        }
        if (accountId != null && !accountId.isBlank()) {
            query.append(sep).append("accountId=").append(encode(accountId));
            sep = "&";
        }
        return client.delete("/rest/api/2/issue/" + issueKey + "/watchers" + query, authHeader);
    }
}
