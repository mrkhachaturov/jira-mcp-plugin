package com.atlassian.mcp.plugin.tools.servicedesk;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class GetServiceDeskForProjectTool implements McpTool {
    private final JiraRestClient client;
    public GetServiceDeskForProjectTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "get_service_desk_for_project"; }
    @Override public String description() { return "Get the service desk configuration for a Jira project."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "project_key", Map.of("type", "string", "description", "Project key")),
                "required", List.of("project_key"));
    }
    @Override public boolean isWriteTool() { return false; }
    @Override public String requiredPluginKey() { return "com.atlassian.servicedesk"; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String pk = (String) args.get("project_key");
        if (pk == null) throw new McpToolException("project_key is required");
        return client.get("/rest/servicedeskapi/servicedesk/projectKey:" + pk, authHeader);
    }
}
