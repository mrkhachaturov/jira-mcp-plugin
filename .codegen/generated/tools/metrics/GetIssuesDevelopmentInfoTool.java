package com.atlassian.mcp.plugin.tools.metrics;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.util.List;
import java.util.Map;

public class GetIssuesDevelopmentInfoTool implements McpTool {
    private final JiraRestClient client;

    public GetIssuesDevelopmentInfoTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_issues_development_info"; }

    @Override
    public String description() {
        return "Get development information for multiple Jira issues. Batch retrieves development panel information (PRs, commits, branches) for multiple issues at once.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_keys", Map.of("type", "string", "description", "Comma-separated list of Jira issue keys (e.g., 'PROJ-123,PROJ-456')"),
                        "application_type", Map.of("type", "string", "description", "(Optional) Filter by application type. Examples: 'stash' (Bitbucket Server), 'bitbucket', 'github', 'gitlab'"),
                        "data_type", Map.of("type", "string", "description", "(Optional) Filter by data type. Examples: 'pullrequest', 'branch', 'repository'")
                ),
                "required", List.of("issue_keys")
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKeys = (String) args.get("issue_keys");
        if (issueKeys == null || issueKeys.isBlank()) {
            throw new McpToolException("'issue_keys' parameter is required");
        }
        String applicationType = (String) args.get("application_type");
        String dataType = (String) args.get("data_type");

        return client.get("/rest/dev-status/latest/issue/detail", authHeader);
    }
}
