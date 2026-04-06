package com.atlassian.mcp.plugin.tools.issues;

// Ported from mcp-atlassian v0.21.0 -- src/mcp_atlassian/servers/jira.py:batch_create_issues
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class BatchCreateIssuesTool implements McpTool {
    private final JiraRestClient client;
    public BatchCreateIssuesTool(JiraRestClient client) { this.client = client; }

    @Override public String name() { return "batch_create_issues"; }
    @Override public String description() {
        return "Create multiple Jira issues in a single batch. Provide an array of issue objects as a JSON string.";
    }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
                "properties", Map.of(
                        "issues_json", Map.of("type", "string", "description",
                                "JSON array of issue objects, each with fields: project_key, summary, issue_type, and optional description/assignee/priority")),
                "required", List.of("issues_json"));
    }
    @Override public boolean isWriteTool() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issuesJson = (String) args.get("issues_json");
        if (issuesJson == null) throw new McpToolException("issues_json is required");
        String body = "{\"issueUpdates\":" + issuesJson + "}";
        return client.post("/rest/api/2/issue/bulk", body, authHeader);
    }
}
