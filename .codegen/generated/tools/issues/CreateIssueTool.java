package com.atlassian.mcp.plugin.tools.issues;

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

    public CreateIssueTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "create_issue"; }

    @Override
    public String description() {
        return "Create a new Jira issue with optional Epic link or parent for subtasks.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "project_key", Map.of("type", "string", "description", "The JIRA project key (e.g. 'PROJ', 'DEV', 'ACV2'). This is the prefix of issue keys in your project. Never assume what it might be, always ask the user."),
                        "summary", Map.of("type", "string", "description", "Summary/title of the issue"),
                        "issue_type", Map.of("type", "string", "description", "Issue type (e.g. 'Task', 'Bug', 'Story', 'Epic', 'Subtask'). The available types depend on your project configuration. For subtasks, use 'Subtask' (not 'Sub-task') and include parent in additional_fields."),
                        "assignee", Map.of("type", "string", "description", "(Optional) Assignee's user identifier (string): Email, display name, or account ID (e.g., 'user@example.com', 'John Doe', 'accountid:...')"),
                        "description", Map.of("type", "string", "description", "Issue description in Markdown format"),
                        "components", Map.of("type", "string", "description", "(Optional) Comma-separated list of component names to assign (e.g., 'Frontend,API')"),
                        "additional_fields", Map.of("type", "string", "description", "(Optional) JSON string of additional fields to set. Examples: - Set priority: {\"priority\": {\"name\": \"High\"}} - Add labels: {\"labels\": [\"frontend\", \"urgent\"]} - Link to parent (for any issue type): {\"parent\": \"PROJ-123\"} - Link to epic: {\"epicKey\": \"EPIC-123\"} or {\"epic_link\": \"EPIC-123\"} - Set Fix Version/s: {\"fixVersions\": [{\"id\": \"10020\"}]} - Custom fields: {\"customfield_10010\": \"value\"}")
                ),
                "required", List.of("project_key", "summary", "issue_type")
        );
    }

    @Override public boolean isWriteTool() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String projectKey = (String) args.get("project_key");
        if (projectKey == null || projectKey.isBlank()) {
            throw new McpToolException("'project_key' parameter is required");
        }
        String summary = (String) args.get("summary");
        if (summary == null || summary.isBlank()) {
            throw new McpToolException("'summary' parameter is required");
        }
        String issueType = (String) args.get("issue_type");
        if (issueType == null || issueType.isBlank()) {
            throw new McpToolException("'issue_type' parameter is required");
        }
        String assignee = (String) args.get("assignee");
        String description = (String) args.get("description");
        String components = (String) args.get("components");
        String additionalFields = (String) args.get("additional_fields");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("project_key", projectKey);
        requestBody.put("summary", summary);
        requestBody.put("issue_type", issueType);
        if (assignee != null) requestBody.put("assignee", assignee);
        if (description != null) requestBody.put("description", description);
        if (components != null) requestBody.put("components", components);
        if (additionalFields != null) requestBody.put("additional_fields", additionalFields);
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            return client.post("/rest/api/2/issue", jsonBody, authHeader);
        } catch (Exception e) {
            throw new McpToolException("Failed to serialize request: " + e.getMessage());
        }
    }
}
