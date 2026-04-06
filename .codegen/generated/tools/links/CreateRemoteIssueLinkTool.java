package com.atlassian.mcp.plugin.tools.links;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateRemoteIssueLinkTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public CreateRemoteIssueLinkTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "create_remote_issue_link"; }

    @Override
    public String description() {
        return "Create a remote issue link (web link or Confluence link) for a Jira issue. This tool allows you to add web links and Confluence links to Jira issues. The links will appear in the issue's \"Links\" section and can be clicked to navigate to external resources.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "The key of the issue to add the link to (e.g., 'PROJ-123', 'ACV2-642')"),
                        "url", Map.of("type", "string", "description", "The URL to link to (e.g., 'https://example.com/page' or Confluence page URL)"),
                        "title", Map.of("type", "string", "description", "The title/name of the link (e.g., 'Documentation Page', 'Confluence Page')"),
                        "summary", Map.of("type", "string", "description", "(Optional) Description of the link"),
                        "relationship", Map.of("type", "string", "description", "(Optional) Relationship description (e.g., 'causes', 'relates to', 'documentation')"),
                        "icon_url", Map.of("type", "string", "description", "(Optional) URL to a 16x16 icon for the link")
                ),
                "required", List.of("issue_key", "url", "title")
        );
    }

    @Override public boolean isWriteTool() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        if (issueKey == null || issueKey.isBlank()) {
            throw new McpToolException("'issue_key' parameter is required");
        }
        String url = (String) args.get("url");
        if (url == null || url.isBlank()) {
            throw new McpToolException("'url' parameter is required");
        }
        String title = (String) args.get("title");
        if (title == null || title.isBlank()) {
            throw new McpToolException("'title' parameter is required");
        }
        String summary = (String) args.get("summary");
        String relationship = (String) args.get("relationship");
        String iconUrl = (String) args.get("icon_url");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("url", url);
        requestBody.put("title", title);
        if (summary != null) requestBody.put("summary", summary);
        if (relationship != null) requestBody.put("relationship", relationship);
        if (iconUrl != null) requestBody.put("icon_url", iconUrl);
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            return client.post("/rest/api/2/issue/" + issueKey + "/remotelink", jsonBody, authHeader);
        } catch (Exception e) {
            throw new McpToolException("Failed to serialize request: " + e.getMessage());
        }
    }
}
