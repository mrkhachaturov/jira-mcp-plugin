package com.atlassian.mcp.plugin.tools.users;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class RemoveWatcherTool implements McpTool {
    private final JiraRestClient client;
    public RemoveWatcherTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "remove_watcher"; }
    @Override public String description() { return "Remove a user from watching a Jira issue."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "issue_key", Map.of("type", "string", "description", "Jira issue key"),
                "username", Map.of("type", "string", "description", "Username to remove")),
                "required", List.of("issue_key", "username"));
    }
    @Override public boolean isWriteTool() { return true; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String ik = (String) args.get("issue_key");
        String username = (String) args.get("username");
        if (ik == null || username == null) throw new McpToolException("issue_key and username required");
        return client.delete("/rest/api/2/issue/" + ik + "/watchers?username=" + username, authHeader);
    }
}
