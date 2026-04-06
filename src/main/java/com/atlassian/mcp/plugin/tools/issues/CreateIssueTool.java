package com.atlassian.mcp.plugin.tools.issues;

// Ported from mcp-atlassian v0.21.0 -- src/mcp_atlassian/servers/jira.py:create_issue
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateIssueTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public CreateIssueTool(JiraRestClient client) { this.client = client; }

    @Override public String name() { return "create_issue"; }
    @Override public String description() {
        return "Create a new Jira issue. Requires project key, summary, and issue type at minimum.";
    }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
                "properties", Map.of(
                        "project_key", Map.of("type", "string", "description", "Project key (e.g., 'PROJ')"),
                        "summary", Map.of("type", "string", "description", "Issue summary/title"),
                        "issue_type", Map.of("type", "string", "description", "Issue type (e.g., 'Task', 'Bug', 'Story')"),
                        "description", Map.of("type", "string", "description", "Issue description"),
                        "assignee", Map.of("type", "string", "description", "Assignee username (Server/DC)"),
                        "priority", Map.of("type", "string", "description", "Priority name (e.g., 'High', 'Medium')"),
                        "labels", Map.of("type", "string", "description", "Comma-separated labels"),
                        "additional_fields", Map.of("type", "string", "description", "JSON string of additional fields")),
                "required", List.of("project_key", "summary", "issue_type"));
    }
    @Override public boolean isWriteTool() { return true; }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String projectKey = (String) args.get("project_key");
        String summary = (String) args.get("summary");
        String issueType = (String) args.get("issue_type");
        if (projectKey == null || summary == null || issueType == null) {
            throw new McpToolException("project_key, summary, and issue_type are required");
        }

        Map<String, Object> fields = new HashMap<>();
        fields.put("project", Map.of("key", projectKey));
        fields.put("summary", summary);
        fields.put("issuetype", Map.of("name", issueType));

        String desc = (String) args.get("description");
        if (desc != null) fields.put("description", desc);
        String assignee = (String) args.get("assignee");
        if (assignee != null) fields.put("assignee", Map.of("name", assignee));
        String priority = (String) args.get("priority");
        if (priority != null) fields.put("priority", Map.of("name", priority));
        String labels = (String) args.get("labels");
        if (labels != null) fields.put("labels", List.of(labels.split(",")));

        String additionalFields = (String) args.get("additional_fields");
        if (additionalFields != null && !additionalFields.isBlank()) {
            try {
                Map<String, Object> extra = mapper.readValue(additionalFields, Map.class);
                fields.putAll(extra);
            } catch (Exception e) {
                throw new McpToolException("Invalid additional_fields JSON: " + e.getMessage());
            }
        }

        try {
            String body = mapper.writeValueAsString(Map.of("fields", fields));
            return client.post("/rest/api/2/issue", body, authHeader);
        } catch (Exception e) {
            if (e instanceof McpToolException) throw (McpToolException) e;
            throw new McpToolException("Failed to serialize issue: " + e.getMessage());
        }
    }
}
