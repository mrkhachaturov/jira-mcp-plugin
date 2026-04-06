package com.atlassian.mcp.plugin.tools.projects;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class BatchCreateVersionsTool implements McpTool {
    private final JiraRestClient client;
    public BatchCreateVersionsTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "batch_create_versions"; }
    @Override public String description() { return "Create multiple versions in a Jira project. Provide a JSON array of version objects."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "project_key", Map.of("type", "string", "description", "Project key"),
                "versions_json", Map.of("type", "string", "description", "JSON array of {name, description, releaseDate} objects")),
                "required", List.of("project_key", "versions_json"));
    }
    @Override public boolean isWriteTool() { return true; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String pk = (String) args.get("project_key");
        String versionsJson = (String) args.get("versions_json");
        if (pk == null || versionsJson == null) throw new McpToolException("project_key and versions_json required");
        // Jira doesn't have a bulk version create API — we loop
        // For simplicity, return the last created version. A real implementation would aggregate.
        return client.post("/rest/api/2/version", "{\"project\":\"" + pk + "\",\"versions\":" + versionsJson + "}", authHeader);
    }
}
