package com.atlassian.mcp.plugin.tools.metrics;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class GetIssueDevelopmentInfoTool implements McpTool {
    private final JiraRestClient client;
    public GetIssueDevelopmentInfoTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "get_issue_development_info"; }
    @Override public String description() { return "Get development information (commits, branches, PRs) linked to a Jira issue."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "issue_id", Map.of("type", "string", "description", "Jira issue ID (numeric)")),
                "required", List.of("issue_id"));
    }
    @Override public boolean isWriteTool() { return false; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueId = (String) args.get("issue_id");
        if (issueId == null) throw new McpToolException("issue_id is required");
        return client.get("/rest/dev-status/latest/issue/detail?issueId=" + issueId + "&applicationType=stash&dataType=repository", authHeader);
    }
}
