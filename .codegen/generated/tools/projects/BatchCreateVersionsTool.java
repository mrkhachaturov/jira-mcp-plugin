package com.atlassian.mcp.plugin.tools.projects;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BatchCreateVersionsTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public BatchCreateVersionsTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "batch_create_versions"; }

    @Override
    public String description() {
        return "Batch create multiple versions in a Jira project.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "project_key", Map.of("type", "string", "description", "Jira project key (e.g., 'PROJ', 'ACV2')"),
                        "versions", Map.of("type", "string", "description", "JSON array of version objects. Each object should contain: - name (required): Name of the version - startDate (optional): Start date (YYYY-MM-DD) - releaseDate (optional): Release date (YYYY-MM-DD) - description (optional): Description of the version Example: [ {\"name\": \"v1.0\", \"startDate\": \"2025-01-01\", \"releaseDate\": \"2025-02-01\", \"description\": \"First release\"}, {\"name\": \"v2.0\"} ]")
                ),
                "required", List.of("project_key", "versions")
        );
    }

    @Override public boolean isWriteTool() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String projectKey = (String) args.get("project_key");
        if (projectKey == null || projectKey.isBlank()) {
            throw new McpToolException("'project_key' parameter is required");
        }
        String versions = (String) args.get("versions");
        if (versions == null || versions.isBlank()) {
            throw new McpToolException("'versions' parameter is required");
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("project_key", projectKey);
        requestBody.put("versions", versions);
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            return client.post("/rest/api/2/version", jsonBody, authHeader);
        } catch (Exception e) {
            throw new McpToolException("Failed to serialize request: " + e.getMessage());
        }
    }
}
