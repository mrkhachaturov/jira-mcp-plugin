package com.atlassian.mcp.plugin.tools.projects;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class GetProjectIssuesTool implements McpTool {
    private final JiraRestClient client;
    public GetProjectIssuesTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "get_project_issues"; }
    @Override public String description() { return "Get issues in a specific Jira project."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "project_key", Map.of("type", "string", "description", "Project key"),
                "maxResults", Map.of("type", "integer", "default", 50)),
                "required", List.of("project_key"));
    }
    @Override public boolean isWriteTool() { return false; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String pk = (String) args.get("project_key");
        if (pk == null) throw new McpToolException("project_key is required");
        String jql = "project = " + pk + " ORDER BY created DESC";
        return client.get("/rest/api/2/search?jql=" + URLEncoder.encode(jql, StandardCharsets.UTF_8) + "&maxResults=50", authHeader);
    }
}
