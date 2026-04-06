package com.atlassian.mcp.plugin.tools.projects;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateVersionTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public CreateVersionTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "create_version"; }

    @Override
    public String description() {
        return "Create a new fix version in a Jira project.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "project_key", Map.of("type", "string", "description", "Jira project key (e.g., 'PROJ', 'ACV2')"),
                        "name", Map.of("type", "string", "description", "Name of the version"),
                        "start_date", Map.of("type", "string", "description", "Start date (YYYY-MM-DD)"),
                        "release_date", Map.of("type", "string", "description", "Release date (YYYY-MM-DD)"),
                        "description", Map.of("type", "string", "description", "Description of the version")
                ),
                "required", List.of("project_key", "name")
        );
    }

    @Override public boolean isWriteTool() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String projectKey = (String) args.get("project_key");
        if (projectKey == null || projectKey.isBlank()) {
            throw new McpToolException("'project_key' parameter is required");
        }
        String name = (String) args.get("name");
        if (name == null || name.isBlank()) {
            throw new McpToolException("'name' parameter is required");
        }
        String startDate = (String) args.get("start_date");
        String releaseDate = (String) args.get("release_date");
        String description = (String) args.get("description");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("project_key", projectKey);
        requestBody.put("name", name);
        if (startDate != null) requestBody.put("start_date", startDate);
        if (releaseDate != null) requestBody.put("release_date", releaseDate);
        if (description != null) requestBody.put("description", description);
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            return client.post("/rest/api/2/version", jsonBody, authHeader);
        } catch (Exception e) {
            throw new McpToolException("Failed to serialize request: " + e.getMessage());
        }
    }
}
