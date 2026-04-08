package com.atlassian.mcp.plugin.tools.links;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LinkToEpicTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public LinkToEpicTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "link_to_epic"; }

    @Override
    public String description() {
        return "Link an existing issue to an epic.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "The key of the issue to link (e.g., 'PROJ-123', 'ACV2-642')"),
                        "epic_key", Map.of("type", "string", "description", "The key of the epic to link to (e.g., 'PROJ-456')")
                ),
                "required", List.of("issue_key", "epic_key")
        );
    }

    @Override public boolean isWriteTool() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        if (issueKey == null || issueKey.isBlank()) {
            throw new McpToolException("'issue_key' parameter is required");
        }
        String epicKey = (String) args.get("epic_key");
        if (epicKey == null || epicKey.isBlank()) {
            throw new McpToolException("'epic_key' parameter is required");
        }

        // Jira Agile API expects: {"issues": ["PROJ-123"]}
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("issues", List.of(issueKey));
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            client.post("/rest/agile/1.0/epic/" + epicKey + "/issue", jsonBody, authHeader);
            // Return the updated issue with a message (matches upstream)
            String updatedIssue = client.get("/rest/api/2/issue/" + issueKey, authHeader);
            Map<String, Object> result = new HashMap<>();
            result.put("message", "Issue " + issueKey + " has been linked to epic " + epicKey + ".");
            result.put("issue", mapper.readTree(updatedIssue));
            return mapper.writeValueAsString(result);
        } catch (McpToolException e) {
            throw e;
        } catch (Exception e) {
            throw new McpToolException("Failed to link issue to epic: " + e.getMessage());
        }
    }
}
