package com.atlassian.mcp.plugin.tools.comments;

// Ported from mcp-atlassian v0.21.0 -- src/mcp_atlassian/servers/jira.py:edit_comment
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class EditCommentTool implements McpTool {
    private final JiraRestClient client;
    public EditCommentTool(JiraRestClient client) { this.client = client; }

    @Override public String name() { return "edit_comment"; }
    @Override public String description() { return "Edit an existing comment on a Jira issue."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key"),
                        "comment_id", Map.of("type", "string", "description", "Comment ID to edit"),
                        "body", Map.of("type", "string", "description", "New comment text")),
                "required", List.of("issue_key", "comment_id", "body"));
    }
    @Override public boolean isWriteTool() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        String commentId = (String) args.get("comment_id");
        String body = (String) args.get("body");
        if (issueKey == null || commentId == null || body == null)
            throw new McpToolException("issue_key, comment_id, and body are required");
        return client.put("/rest/api/2/issue/" + issueKey + "/comment/" + commentId,
                "{\"body\":\"" + body.replace("\"", "\\\"").replace("\n", "\\n") + "\"}", authHeader);
    }
}
