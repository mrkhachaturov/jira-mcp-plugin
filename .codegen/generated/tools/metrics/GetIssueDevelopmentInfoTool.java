package com.atlassian.mcp.plugin.tools.metrics;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class GetIssueDevelopmentInfoTool implements McpTool {
    private final JiraRestClient client;

    public GetIssueDevelopmentInfoTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_issue_development_info"; }

    @Override
    public String description() {
        return "Get development information (PRs, commits, branches) linked to a Jira issue. This retrieves the development panel information that shows linked pull requests, branches, and commits from connected source control systems like Bitbucket, GitHub, or GitLab.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123')"),
                        "application_type", Map.of("type", "string", "description", "(Optional) Filter by application type. Examples: 'stash' (Bitbucket Server), 'bitbucket', 'github', 'gitlab'"),
                        "data_type", Map.of("type", "string", "description", "(Optional) Filter by data type. Examples: 'pullrequest', 'branch', 'repository'")
                ),
                "required", List.of("issue_key")
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        if (issueKey == null || issueKey.isBlank()) {
            throw new McpToolException("'issue_key' parameter is required");
        }
        String applicationType = (String) args.get("application_type");
        String dataType = (String) args.get("data_type");

        StringBuilder query = new StringBuilder();
        String sep = "?";
        if (issueKey != null && !issueKey.isBlank()) {
            query.append(sep).append("issueId=").append(encode(issueKey));
            sep = "&";
        }

        return client.get("/rest/dev-status/latest/issue/detail" + query, authHeader);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
