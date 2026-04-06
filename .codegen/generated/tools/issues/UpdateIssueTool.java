package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpdateIssueTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public UpdateIssueTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "update_issue"; }

    @Override
    public String description() {
        return "Update an existing Jira issue including changing status, adding Epic links, updating fields, etc.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')"),
                        "fields", Map.of("type", "string", "description", "JSON string of fields to update. For 'assignee', provide a string identifier (email, name, or accountId). For 'description', provide text in Markdown format. Example: '{\"assignee\": \"user@example.com\", \"summary\": \"New Summary\", \"description\": \"## Updated\\nMarkdown text\"}'"),
                        "additional_fields", Map.of("type", "string", "description", "(Optional) JSON string of additional fields to update. Use this for custom fields or more complex updates. Link to epic: {\"epicKey\": \"EPIC-123\"} or {\"epic_link\": \"EPIC-123\"}."),
                        "components", Map.of("type", "string", "description", "(Optional) Comma-separated list of component names (e.g., 'Frontend,API')"),
                        "attachments", Map.of("type", "string", "description", "(Optional) JSON string array or comma-separated list of file paths to attach to the issue. Example: '/path/to/file1.txt,/path/to/file2.txt' or ['/path/to/file1.txt','/path/to/file2.txt']")
                ),
                "required", List.of("issue_key", "fields")
        );
    }

    @Override public boolean isWriteTool() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        if (issueKey == null || issueKey.isBlank()) {
            throw new McpToolException("'issue_key' parameter is required");
        }
        String fields = (String) args.get("fields");
        if (fields == null || fields.isBlank()) {
            throw new McpToolException("'fields' parameter is required");
        }
        String additionalFields = (String) args.get("additional_fields");
        String components = (String) args.get("components");
        String attachments = (String) args.get("attachments");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("fields", fields);
        if (additionalFields != null) requestBody.put("additional_fields", additionalFields);
        if (components != null) requestBody.put("components", components);
        if (attachments != null) requestBody.put("attachments", attachments);
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            return client.put("/rest/api/2/issue/" + issueKey, jsonBody, authHeader);
        } catch (Exception e) {
            throw new McpToolException("Failed to serialize request: " + e.getMessage());
        }
    }
}
