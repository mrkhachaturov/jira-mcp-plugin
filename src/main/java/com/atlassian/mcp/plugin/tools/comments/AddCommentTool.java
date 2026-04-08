package com.atlassian.mcp.plugin.tools.comments;

import com.atlassian.mcp.plugin.JiraMarkupConverter;
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddCommentTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public AddCommentTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "add_comment"; }

    @Override
    public String description() {
        return "Add a comment to a Jira issue.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')"),
                        "body", Map.of("type", "string", "description", "Comment text in Markdown format"),
                        "visibility", Map.of("type", "string", "description", "(Optional) Comment visibility as JSON string (e.g. '{\"type\":\"group\",\"value\":\"jira-users\"}')"),
                        "public", Map.of("type", "boolean", "description", "(Optional) For JSM/Service Desk issues only. Set to true for customer-visible comment, false for internal agent-only comment. Uses the ServiceDesk API (plain text, not Markdown). Cannot be combined with visibility.")
                ),
                "required", List.of("issue_key", "body")
        );
    }

    @Override public boolean isWriteTool() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        if (issueKey == null || issueKey.isBlank()) {
            throw new McpToolException("'issue_key' parameter is required");
        }
        String body = (String) args.get("body");
        if (body == null || body.isBlank()) {
            throw new McpToolException("'body' parameter is required");
        }
        String visibility = (String) args.get("visibility");
        boolean isPublic = getBoolean(args, "public", false);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("body", JiraMarkupConverter.markdownToJira(body));
        if (visibility != null) requestBody.put("visibility", visibility);
        requestBody.put("public", isPublic);
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            return client.post("/rest/api/2/issue/" + issueKey + "/comment", jsonBody, authHeader);
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
