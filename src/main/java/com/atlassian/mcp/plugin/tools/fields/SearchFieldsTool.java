package com.atlassian.mcp.plugin.tools.fields;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class SearchFieldsTool implements McpTool {
    private final JiraRestClient client;
    public SearchFieldsTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "search_fields"; }
    @Override public String description() { return "Search or list available Jira fields."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "keyword", Map.of("type", "string", "description", "Filter fields by keyword (optional)")),
                "required", List.of());
    }
    @Override public boolean isWriteTool() { return false; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        return client.get("/rest/api/2/field", authHeader);
    }
}
