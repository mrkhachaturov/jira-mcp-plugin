package com.atlassian.mcp.plugin.tools.metrics;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    @Override public boolean supportsProgress() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        return executeWithProgress(args, authHeader, (current, total, message) -> {});
    }

    @Override
    public String executeWithProgress(Map<String, Object> args, String authHeader,
                                      ProgressCallback progress) throws McpToolException {
        String issueKeys = (String) args.get("issue_keys");
        if (issueKeys == null || issueKeys.isBlank()) {
            throw new McpToolException("'issue_keys' parameter is required");
        }
        String applicationType = (String) args.get("application_type");
        String dataType = (String) args.get("data_type");

        String[] keys = issueKeys.split(",");
        List<String> trimmedKeys = new ArrayList<>();
        for (String k : keys) {
            String t = k.trim();
            if (!t.isEmpty()) trimmedKeys.add(t);
        }

        int total = trimmedKeys.size();
        List<String> results = new ArrayList<>();

        for (int i = 0; i < total; i++) {
            String key = trimmedKeys.get(i);
            progress.report(i, total, "Fetching dev info for " + key + " (" + (i + 1) + "/" + total + ")");

            StringBuilder query = new StringBuilder("?issueKey=");
            query.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
            if (applicationType != null && !applicationType.isBlank()) {
                query.append("&applicationType=").append(URLEncoder.encode(applicationType, StandardCharsets.UTF_8));
            }
            if (dataType != null && !dataType.isBlank()) {
                query.append("&dataType=").append(URLEncoder.encode(dataType, StandardCharsets.UTF_8));
            }

            String devInfo = client.get("/rest/dev-status/latest/issue/detail" + query, authHeader);
            results.add("\"" + key + "\":" + devInfo);
        }

        progress.report(total, total, "Completed: dev info for " + total + " issues");

        return "{" + String.join(",", results) + "}";
    }
}
