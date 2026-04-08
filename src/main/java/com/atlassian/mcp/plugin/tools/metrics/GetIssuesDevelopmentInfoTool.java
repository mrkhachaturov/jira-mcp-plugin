package com.atlassian.mcp.plugin.tools.metrics;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Batch version of get_issue_development_info.
 * Delegates to GetIssueDevelopmentInfoTool per-issue (matches upstream pattern).
 */
public class GetIssuesDevelopmentInfoTool implements McpTool {
    private final JiraRestClient client;
    private final GetIssueDevelopmentInfoTool singleTool;
    private final ObjectMapper mapper = new ObjectMapper();

    public GetIssuesDevelopmentInfoTool(JiraRestClient client) {
        this.client = client;
        this.singleTool = new GetIssueDevelopmentInfoTool(client);
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
        List<Object> results = new ArrayList<>();

        for (int i = 0; i < total; i++) {
            String key = trimmedKeys.get(i);
            progress.report(i, total, "Fetching dev info for " + key + " (" + (i + 1) + "/" + total + ")");

            try {
                // Delegate to single-issue tool (handles numeric ID resolution etc.)
                Map<String, Object> singleArgs = new LinkedHashMap<>();
                singleArgs.put("issue_key", key);
                if (applicationType != null) singleArgs.put("application_type", applicationType);
                if (dataType != null) singleArgs.put("data_type", dataType);

                String devInfo = singleTool.execute(singleArgs, authHeader);
                results.add(mapper.readTree(devInfo));
            } catch (Exception e) {
                Map<String, Object> errorResult = new LinkedHashMap<>();
                errorResult.put("issue_key", key);
                errorResult.put("error", e.getMessage());
                errorResult.put("pullRequests", List.of());
                errorResult.put("branches", List.of());
                errorResult.put("commits", List.of());
                results.add(errorResult);
            }
        }

        progress.report(total, total, "Completed: " + total + " issues processed");

        try {
            return mapper.writeValueAsString(results);
        } catch (Exception e) {
            throw new McpToolException("Failed to serialize results: " + e.getMessage());
        }
    }
}
