package com.atlassian.mcp.plugin.tools.worklogs;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddWorklogTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public AddWorklogTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "add_worklog"; }

    @Override
    public String description() {
        return "Add a worklog entry to a Jira issue.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')"),
                        "time_spent", Map.of("type", "string", "description", "Time spent in Jira format. Examples: '1h 30m' (1 hour and 30 minutes), '1d' (1 day), '30m' (30 minutes), '4h' (4 hours)"),
                        "comment", Map.of("type", "string", "description", "(Optional) Comment for the worklog in Markdown format"),
                        "started", Map.of("type", "string", "description", "(Optional) Start time in ISO format. If not provided, the current time will be used. Example: '2023-08-01T12:00:00.000+0000'"),
                        "original_estimate", Map.of("type", "string", "description", "(Optional) New value for the original estimate"),
                        "remaining_estimate", Map.of("type", "string", "description", "(Optional) New value for the remaining estimate")
                ),
                "required", List.of("issue_key", "time_spent")
        );
    }

    @Override public boolean isWriteTool() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        if (issueKey == null || issueKey.isBlank()) {
            throw new McpToolException("'issue_key' parameter is required");
        }
        String timeSpent = (String) args.get("time_spent");
        if (timeSpent == null || timeSpent.isBlank()) {
            throw new McpToolException("'time_spent' parameter is required");
        }
        String comment = (String) args.get("comment");
        String started = (String) args.get("started");
        String originalEstimate = (String) args.get("original_estimate");
        String remainingEstimate = (String) args.get("remaining_estimate");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("time_spent", timeSpent);
        if (comment != null) requestBody.put("comment", comment);
        if (started != null) requestBody.put("started", started);
        if (originalEstimate != null) requestBody.put("original_estimate", originalEstimate);
        if (remainingEstimate != null) requestBody.put("remaining_estimate", remainingEstimate);
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            return client.post("/rest/api/2/issue/" + issueKey + "/worklog", jsonBody, authHeader);
        } catch (Exception e) {
            throw new McpToolException("Failed to serialize request: " + e.getMessage());
        }
    }
}
