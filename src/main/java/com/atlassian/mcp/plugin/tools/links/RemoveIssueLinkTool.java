package com.atlassian.mcp.plugin.tools.links;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class RemoveIssueLinkTool implements McpTool {
    private final JiraRestClient client;
    public RemoveIssueLinkTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "remove_issue_link"; }
    @Override public String description() { return "Remove an issue link by link ID."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "link_id", Map.of("type", "string", "description", "Issue link ID to remove")),
                "required", List.of("link_id"));
    }
    @Override public boolean isWriteTool() { return true; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String linkId = (String) args.get("link_id");
        if (linkId == null) throw new McpToolException("link_id is required");
        return client.delete("/rest/api/2/issueLink/" + linkId, authHeader);
    }
}
