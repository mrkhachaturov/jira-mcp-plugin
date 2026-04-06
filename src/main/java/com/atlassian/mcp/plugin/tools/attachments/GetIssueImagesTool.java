package com.atlassian.mcp.plugin.tools.attachments;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class GetIssueImagesTool implements McpTool {
    private final JiraRestClient client;
    public GetIssueImagesTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "get_issue_images"; }
    @Override public String description() { return "Get image attachments for a Jira issue. Returns attachment metadata filtered to image types."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "issue_key", Map.of("type", "string", "description", "Jira issue key")),
                "required", List.of("issue_key"));
    }
    @Override public boolean isWriteTool() { return false; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String ik = (String) args.get("issue_key");
        if (ik == null) throw new McpToolException("issue_key is required");
        return client.get("/rest/api/2/issue/" + ik + "?fields=attachment", authHeader);
    }
}
