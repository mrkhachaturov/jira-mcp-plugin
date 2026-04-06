package com.atlassian.mcp.plugin.tools.links;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateIssueLinkTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public CreateIssueLinkTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "create_issue_link"; }

    @Override
    public String description() {
        return "Create a link between two Jira issues.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "link_type", Map.of("type", "string", "description", "The type of link to create (e.g., 'Duplicate', 'Blocks', 'Relates to')"),
                        "inward_issue_key", Map.of("type", "string", "description", "The key of the inward issue (e.g., 'PROJ-123', 'ACV2-642')"),
                        "outward_issue_key", Map.of("type", "string", "description", "The key of the outward issue (e.g., 'PROJ-456')"),
                        "comment", Map.of("type", "string", "description", "(Optional) Comment to add to the link"),
                        "comment_visibility", Map.of("type", "string", "description", "(Optional) Visibility settings for the comment as JSON string (e.g. '{\"type\":\"group\",\"value\":\"jira-users\"}')")
                ),
                "required", List.of("link_type", "inward_issue_key", "outward_issue_key")
        );
    }

    @Override public boolean isWriteTool() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String linkType = (String) args.get("link_type");
        if (linkType == null || linkType.isBlank()) {
            throw new McpToolException("'link_type' parameter is required");
        }
        String inwardIssueKey = (String) args.get("inward_issue_key");
        if (inwardIssueKey == null || inwardIssueKey.isBlank()) {
            throw new McpToolException("'inward_issue_key' parameter is required");
        }
        String outwardIssueKey = (String) args.get("outward_issue_key");
        if (outwardIssueKey == null || outwardIssueKey.isBlank()) {
            throw new McpToolException("'outward_issue_key' parameter is required");
        }
        String comment = (String) args.get("comment");
        String commentVisibility = (String) args.get("comment_visibility");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("link_type", linkType);
        requestBody.put("inward_issue_key", inwardIssueKey);
        requestBody.put("outward_issue_key", outwardIssueKey);
        if (comment != null) requestBody.put("comment", comment);
        if (commentVisibility != null) requestBody.put("comment_visibility", commentVisibility);
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            return client.post("/rest/api/2/issueLink", jsonBody, authHeader);
        } catch (Exception e) {
            throw new McpToolException("Failed to serialize request: " + e.getMessage());
        }
    }
}
