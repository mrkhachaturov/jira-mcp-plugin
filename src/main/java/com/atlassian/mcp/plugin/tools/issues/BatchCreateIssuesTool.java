package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

public class BatchCreateIssuesTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public BatchCreateIssuesTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "batch_create_issues"; }

    @Override
    public String description() {
        return "Create multiple Jira issues in a batch.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issues", Map.of("type", "string", "description", "JSON array of issue objects. Each object should contain: - project_key (required): The project key (e.g., 'PROJ') - summary (required): Issue summary/title - issue_type (required): Type of issue (e.g., 'Task', 'Bug') - description (optional): Issue description in Markdown format - assignee (optional): Assignee username or email - components (optional): Array of component names Example: [ {\"project_key\": \"PROJ\", \"summary\": \"Issue 1\", \"issue_type\": \"Task\"}, {\"project_key\": \"PROJ\", \"summary\": \"Issue 2\", \"issue_type\": \"Bug\", \"components\": [\"Frontend\"]} ]"),
                        "validate_only", Map.of("type", "boolean", "description", "If true, only validates the issues without creating them", "default", false)
                ),
                "required", List.of("issues")
        );
    }

    @Override public boolean isWriteTool() { return true; }
    @Override public boolean supportsProgress() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        return executeWithProgress(args, authHeader, (current, total, message) -> {});
    }

    @Override
    public String executeWithProgress(Map<String, Object> args, String authHeader,
                                      ProgressCallback progress) throws McpToolException {
        String issuesJson = (String) args.get("issues");
        if (issuesJson == null || issuesJson.isBlank()) {
            throw new McpToolException("'issues' parameter is required");
        }

        List<Map<String, Object>> issues;
        try {
            issues = mapper.readValue(issuesJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new McpToolException("Invalid issues JSON: " + e.getMessage());
        }

        int total = issues.size();
        List<Map<String, Object>> created = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();

        for (int i = 0; i < total; i++) {
            Map<String, Object> issue = issues.get(i);
            String summary = (String) issue.getOrDefault("summary", "?");

            progress.report(i, total, "Creating issue " + (i + 1) + " of " + total + ": " + summary);

            try {
                // Build Jira fields structure
                Map<String, Object> fields = new HashMap<>();
                fields.put("project", Map.of("key", issue.get("project_key")));
                fields.put("summary", issue.get("summary"));
                fields.put("issuetype", Map.of("name", issue.get("issue_type")));

                if (issue.containsKey("description"))
                    fields.put("description", issue.get("description"));
                if (issue.containsKey("assignee"))
                    fields.put("assignee", Map.of("name", issue.get("assignee")));

                String body = mapper.writeValueAsString(Map.of("fields", fields));
                String result = client.post("/rest/api/2/issue", body, authHeader);
                Map<String, Object> parsed = mapper.readValue(result, new TypeReference<>() {});
                created.add(parsed);
            } catch (Exception e) {
                errors.add(Map.of(
                        "index", i,
                        "summary", summary,
                        "error", e.getMessage()
                ));
            }
        }

        progress.report(total, total, "Completed: " + created.size() + " created, " + errors.size() + " errors");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", created.size());
        result.put("errors", errors.size());
        result.put("issues", created);
        if (!errors.isEmpty()) {
            result.put("failed", errors);
        }

        try {
            return mapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new McpToolException("Failed to serialize result: " + e.getMessage());
        }
    }
}
