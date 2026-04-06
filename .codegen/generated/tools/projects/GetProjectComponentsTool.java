package com.atlassian.mcp.plugin.tools.projects;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.util.List;
import java.util.Map;

public class GetProjectComponentsTool implements McpTool {
    private final JiraRestClient client;

    public GetProjectComponentsTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_project_components"; }

    @Override
    public String description() {
        return "Get all components for a specific Jira project.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "project_key", Map.of("type", "string", "description", "Jira project key (e.g., 'PROJ', 'ACV2')")
                ),
                "required", List.of("project_key")
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String projectKey = (String) args.get("project_key");
        if (projectKey == null || projectKey.isBlank()) {
            throw new McpToolException("'project_key' parameter is required");
        }

        return client.get("/rest/api/2/project/" + projectKey + "/components", authHeader);
    }
}
