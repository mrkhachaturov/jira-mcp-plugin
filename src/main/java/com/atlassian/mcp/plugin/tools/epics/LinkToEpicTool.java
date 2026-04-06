package com.atlassian.mcp.plugin.tools.epics;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class LinkToEpicTool implements McpTool {
    private final JiraRestClient client;
    public LinkToEpicTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "link_to_epic"; }
    @Override public String description() { return "Link an issue to an epic."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "epic_key", Map.of("type", "string", "description", "Epic issue key"),
                "issue_key", Map.of("type", "string", "description", "Issue key to link to epic")),
                "required", List.of("epic_key", "issue_key"));
    }
    @Override public boolean isWriteTool() { return true; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String epicKey = (String) args.get("epic_key");
        String issueKey = (String) args.get("issue_key");
        if (epicKey == null || issueKey == null) throw new McpToolException("epic_key and issue_key required");
        String body = "{\"issues\":[\"" + issueKey + "\"]}";
        return client.post("/rest/agile/1.0/epic/" + epicKey + "/issue", body, authHeader);
    }
}
