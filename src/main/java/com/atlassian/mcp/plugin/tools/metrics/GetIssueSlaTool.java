package com.atlassian.mcp.plugin.tools.metrics;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class GetIssueSlaTool implements McpTool {
    private final JiraRestClient client;
    public GetIssueSlaTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "get_issue_sla"; }
    @Override public String description() { return "Get SLA metrics for a Jira Service Desk issue."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "issue_key", Map.of("type", "string", "description", "Jira issue key")),
                "required", List.of("issue_key"));
    }
    @Override public boolean isWriteTool() { return false; }
    @Override public String requiredPluginKey() { return "com.atlassian.servicedesk"; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String ik = (String) args.get("issue_key");
        if (ik == null) throw new McpToolException("issue_key is required");
        return client.get("/rest/servicedeskapi/request/" + ik + "/sla", authHeader);
    }
}
