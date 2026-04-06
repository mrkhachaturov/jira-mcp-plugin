package com.atlassian.mcp.plugin.tools.comments;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditCommentTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public EditCommentTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "edit_comment"; }

    @Override
    public String description() {
        return "Edit an existing comment on a Jira issue.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')"),
                        "comment_id", Map.of("type", "string", "description", "The ID of the comment to edit"),
                        "body", Map.of("type", "string", "description", "Updated comment text in Markdown format"),
                        "visibility", Map.of("type", "string", "description", "(Optional) Comment visibility as JSON string (e.g. '{\"type\":\"group\",\"value\":\"jira-users\"}')")
                ),
                "required", List.of("issue_key", "comment_id", "body")
        );
    }

    @Override public boolean isWriteTool() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        if (issueKey == null || issueKey.isBlank()) {
            throw new McpToolException("'issue_key' parameter is required");
        }
        String commentId = (String) args.get("comment_id");
        if (commentId == null || commentId.isBlank()) {
            throw new McpToolException("'comment_id' parameter is required");
        }
        String body = (String) args.get("body");
        if (body == null || body.isBlank()) {
            throw new McpToolException("'body' parameter is required");
        }
        String visibility = (String) args.get("visibility");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("body", body);
        if (visibility != null) requestBody.put("visibility", visibility);
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            return client.put("/rest/api/2/issue/" + issueKey + "/comment/" + commentId, jsonBody, authHeader);
        } catch (Exception e) {
            throw new McpToolException("Failed to serialize request: " + e.getMessage());
        }
    }
}
