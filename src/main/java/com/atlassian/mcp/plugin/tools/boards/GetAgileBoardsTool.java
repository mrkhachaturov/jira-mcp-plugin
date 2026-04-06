package com.atlassian.mcp.plugin.tools.boards;

// Ported from mcp-atlassian v0.21.0 -- src/mcp_atlassian/servers/jira.py:get_agile_boards
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class GetAgileBoardsTool implements McpTool {
    private final JiraRestClient client;
    public GetAgileBoardsTool(JiraRestClient client) { this.client = client; }

    @Override public String name() { return "get_agile_boards"; }
    @Override public String description() { return "List Jira Agile boards. Optionally filter by name or project."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "description", "Filter boards by name (substring match)"),
                        "project_key", Map.of("type", "string", "description", "Filter boards by project key"),
                        "maxResults", Map.of("type", "integer", "description", "Max results", "default", 50)),
                "required", List.of());
    }
    @Override public boolean isWriteTool() { return false; }
    @Override public String requiredPluginKey() { return "com.atlassian.jira.plugins.jira-software-plugin"; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        StringBuilder q = new StringBuilder("/rest/agile/1.0/board?maxResults=50");
        String name = (String) args.get("name");
        if (name != null && !name.isBlank()) q.append("&name=").append(URLEncoder.encode(name, StandardCharsets.UTF_8));
        String projectKey = (String) args.get("project_key");
        if (projectKey != null && !projectKey.isBlank()) q.append("&projectKeyOrId=").append(projectKey);
        return client.get(q.toString(), authHeader);
    }
}
