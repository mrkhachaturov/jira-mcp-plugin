package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issues = (String) args.get("issues");
        if (issues == null || issues.isBlank()) {
            throw new McpToolException("'issues' parameter is required");
        }
        boolean validateOnly = getBoolean(args, "validate_only", false);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("issues", issues);
        requestBody.put("validate_only", validateOnly);
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            return client.post("/rest/api/2/issue/bulk", jsonBody, authHeader);
        } catch (Exception e) {
            throw new McpToolException("Failed to serialize request: " + e.getMessage());
        }
    }

    private static boolean getBoolean(Map<String, Object> args, String key, boolean defaultVal) {
        Object val = args.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return "true".equalsIgnoreCase(s);
        return defaultVal;
    }
}
