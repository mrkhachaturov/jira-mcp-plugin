package com.atlassian.mcp.plugin.tools.fields;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class GetFieldOptionsTool implements McpTool {
    private final JiraRestClient client;
    public GetFieldOptionsTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "get_field_options"; }
    @Override public String description() { return "Get available options for a select/multi-select Jira field."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "field_key", Map.of("type", "string", "description", "Field key or ID (e.g., 'customfield_10100')")),
                "required", List.of("field_key"));
    }
    @Override public boolean isWriteTool() { return false; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String fieldKey = (String) args.get("field_key");
        if (fieldKey == null) throw new McpToolException("field_key is required");
        return client.get("/rest/api/2/field/" + fieldKey + "/option", authHeader);
    }
}
