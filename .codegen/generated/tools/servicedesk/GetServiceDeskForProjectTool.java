package com.atlassian.mcp.plugin.tools.servicedesk;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.util.List;
import java.util.Map;

public class GetServiceDeskForProjectTool implements McpTool {
    private final JiraRestClient client;

    public GetServiceDeskForProjectTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_service_desk_for_project"; }

    @Override
    public String description() {
        return "Get the Jira Service Desk associated with a project key. Server/Data Center only. Not available on Jira Cloud.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "project_key", Map.of("type", "string", "description", "Jira project key (e.g., 'SUP')")
                ),
                "required", List.of("project_key")
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String requiredPluginKey() { return "com.atlassian.servicedesk"; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String projectKey = (String) args.get("project_key");
        if (projectKey == null || projectKey.isBlank()) {
            throw new McpToolException("'project_key' parameter is required");
        }

        return client.get("/rest/servicedeskapi/servicedesk", authHeader);
    }
}
