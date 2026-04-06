package com.atlassian.mcp.plugin.tools.users;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class GetIssueWatchersTool implements McpTool {
    private final JiraRestClient client;
    public GetIssueWatchersTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "get_issue_watchers"; }
    @Override public String description() { return "Get the list of watchers for a Jira issue."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "issue_key", Map.of("type", "string", "description", "Jira issue key")),
                "required", List.of("issue_key"));
    }
    @Override public boolean isWriteTool() { return false; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String ik = (String) args.get("issue_key");
        if (ik == null) throw new McpToolException("issue_key is required");
        return client.get("/rest/api/2/issue/" + ik + "/watchers", authHeader);
    }
}
