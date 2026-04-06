package com.atlassian.mcp.plugin.tools.projects;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

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
    @Override public boolean supportsProgress() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        return executeWithProgress(args, authHeader, (current, total, message) -> {});
    }

    @Override
    public String executeWithProgress(Map<String, Object> args, String authHeader,
                                      ProgressCallback progress) throws McpToolException {
        String projectKey = (String) args.get("project_key");
        if (projectKey == null || projectKey.isBlank()) {
            throw new McpToolException("'project_key' parameter is required");
        }
        String versionsJson = (String) args.get("versions");
        if (versionsJson == null || versionsJson.isBlank()) {
            throw new McpToolException("'versions' parameter is required");
        }

        List<Map<String, Object>> versions;
        try {
            versions = mapper.readValue(versionsJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new McpToolException("Invalid versions JSON: " + e.getMessage());
        }

        int total = versions.size();
        List<Map<String, Object>> created = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();

        for (int i = 0; i < total; i++) {
            Map<String, Object> version = versions.get(i);
            String name = (String) version.getOrDefault("name", "?");

            progress.report(i, total, "Creating version " + (i + 1) + " of " + total + ": " + name);

            try {
                Map<String, Object> body = new HashMap<>(version);
                body.put("project", projectKey);
                String jsonBody = mapper.writeValueAsString(body);
                String result = client.post("/rest/api/2/version", jsonBody, authHeader);
                created.add(mapper.readValue(result, new TypeReference<>() {}));
            } catch (Exception e) {
                errors.add(Map.of("index", i, "name", name, "error", e.getMessage()));
            }
        }

        progress.report(total, total, "Completed: " + created.size() + " created, " + errors.size() + " errors");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", created.size());
        result.put("errors", errors.size());
        result.put("versions", created);
        if (!errors.isEmpty()) result.put("failed", errors);

        try {
            return mapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new McpToolException("Failed to serialize result: " + e.getMessage());
        }
    }
}
