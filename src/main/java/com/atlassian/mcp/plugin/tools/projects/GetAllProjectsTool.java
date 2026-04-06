package com.atlassian.mcp.plugin.tools.projects;

// Ported from mcp-atlassian v0.21.0 -- src/mcp_atlassian/servers/jira.py:get_all_projects
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class GetAllProjectsTool implements McpTool {

    private final JiraRestClient client;

    public GetAllProjectsTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_all_projects"; }

    @Override public String description() {
        return "Get all Jira projects accessible by the current user.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(),
                "required", List.of()
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        return client.get("/rest/api/2/project", authHeader);
    }
}
