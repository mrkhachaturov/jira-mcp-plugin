package com.atlassian.mcp.plugin.tools.worklogs;

// Ported from mcp-atlassian v0.21.0 -- src/mcp_atlassian/servers/jira.py:add_worklog
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class AddWorklogTool implements McpTool {
    private final JiraRestClient client;
    public AddWorklogTool(JiraRestClient client) { this.client = client; }

    @Override public String name() { return "add_worklog"; }
    @Override public String description() { return "Log time (worklog) on a Jira issue."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key"),
                        "time_spent", Map.of("type", "string", "description", "Time spent (e.g., '2h 30m', '1d')"),
                        "comment", Map.of("type", "string", "description", "Worklog comment")),
                "required", List.of("issue_key", "time_spent"));
    }
    @Override public boolean isWriteTool() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        String timeSpent = (String) args.get("time_spent");
        if (issueKey == null || timeSpent == null)
            throw new McpToolException("issue_key and time_spent are required");
        String comment = (String) args.getOrDefault("comment", "");
        String body = "{\"timeSpent\":\"" + timeSpent + "\",\"comment\":\"" + comment.replace("\"", "\\\"") + "\"}";
        return client.post("/rest/api/2/issue/" + issueKey + "/worklog", body, authHeader);
    }
}
