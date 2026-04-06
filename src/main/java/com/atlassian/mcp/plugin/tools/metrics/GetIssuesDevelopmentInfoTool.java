package com.atlassian.mcp.plugin.tools.metrics;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class GetIssuesDevelopmentInfoTool implements McpTool {
    private final JiraRestClient client;
    public GetIssuesDevelopmentInfoTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "get_issues_development_info"; }
    @Override public String description() { return "Get development summary (commits, branches, PRs) for multiple Jira issues."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "issue_ids", Map.of("type", "string", "description", "Comma-separated Jira issue IDs (numeric)")),
                "required", List.of("issue_ids"));
    }
    @Override public boolean isWriteTool() { return false; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueIds = (String) args.get("issue_ids");
        if (issueIds == null) throw new McpToolException("issue_ids is required");
        return client.get("/rest/dev-status/latest/issue/summary?issueId=" + issueIds, authHeader);
    }
}
