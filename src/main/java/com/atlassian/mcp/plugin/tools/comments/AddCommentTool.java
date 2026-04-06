package com.atlassian.mcp.plugin.tools.comments;

// Ported from mcp-atlassian v0.21.0 -- src/mcp_atlassian/servers/jira.py:add_comment
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class AddCommentTool implements McpTool {
    private final JiraRestClient client;
    public AddCommentTool(JiraRestClient client) { this.client = client; }

    @Override public String name() { return "add_comment"; }
    @Override public String description() { return "Add a comment to a Jira issue."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key"),
                        "body", Map.of("type", "string", "description", "Comment text")),
                "required", List.of("issue_key", "body"));
    }
    @Override public boolean isWriteTool() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        String body = (String) args.get("body");
        if (issueKey == null || body == null) throw new McpToolException("issue_key and body are required");
        return client.post("/rest/api/2/issue/" + issueKey + "/comment",
                "{\"body\":\"" + body.replace("\"", "\\\"").replace("\n", "\\n") + "\"}", authHeader);
    }
}
