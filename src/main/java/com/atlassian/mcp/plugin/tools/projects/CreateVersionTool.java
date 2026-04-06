package com.atlassian.mcp.plugin.tools.projects;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class CreateVersionTool implements McpTool {
    private final JiraRestClient client;
    public CreateVersionTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "create_version"; }
    @Override public String description() { return "Create a new version in a Jira project."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "project_key", Map.of("type", "string", "description", "Project key"),
                "name", Map.of("type", "string", "description", "Version name"),
                "description", Map.of("type", "string", "description", "Version description"),
                "release_date", Map.of("type", "string", "description", "Release date (YYYY-MM-DD)")),
                "required", List.of("project_key", "name"));
    }
    @Override public boolean isWriteTool() { return true; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String pk = (String) args.get("project_key");
        String name = (String) args.get("name");
        if (pk == null || name == null) throw new McpToolException("project_key and name required");
        StringBuilder body = new StringBuilder("{\"project\":\"" + pk + "\",\"name\":\"" + name.replace("\"", "\\\"") + "\"");
        String desc = (String) args.get("description");
        if (desc != null) body.append(",\"description\":\"").append(desc.replace("\"", "\\\"")).append("\"");
        String releaseDate = (String) args.get("release_date");
        if (releaseDate != null) body.append(",\"releaseDate\":\"").append(releaseDate).append("\"");
        body.append("}");
        return client.post("/rest/api/2/version", body.toString(), authHeader);
    }
}
