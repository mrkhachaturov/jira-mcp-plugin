package com.atlassian.mcp.plugin.tools.links;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class GetLinkTypesTool implements McpTool {
    private final JiraRestClient client;
    public GetLinkTypesTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "get_link_types"; }
    @Override public String description() { return "Get available issue link types."; }
    @Override public Map<String, Object> inputSchema() { return Map.of("type", "object", "properties", Map.of(), "required", List.of()); }
    @Override public boolean isWriteTool() { return false; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        return client.get("/rest/api/2/issueLinkType", authHeader);
    }
}
