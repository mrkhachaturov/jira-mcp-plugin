package com.atlassian.mcp.plugin.tools.issues;

// Ported from mcp-atlassian v0.21.0 -- src/mcp_atlassian/servers/jira.py:get_issue
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class GetIssueTool implements McpTool {

    private final JiraRestClient client;

    public GetIssueTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_issue"; }

    @Override public String description() {
        return "Get details of a specific Jira issue including its fields, "
                + "comments, and relationship information.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of(
                                "type", "string",
                                "description", "Jira issue key (e.g., 'PROJ-123')"
                        ),
                        "fields", Map.of(
                                "type", "string",
                                "description", "Comma-separated list of fields to return, or '*all' for everything",
                                "default", "summary,status,assignee,reporter,priority,issuetype,created,updated,description,comment"
                        ),
                        "expand", Map.of(
                                "type", "string",
                                "description", "Fields to expand (e.g., 'renderedFields,transitions,changelog')"
                        )
                ),
                "required", List.of("issue_key")
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        if (issueKey == null || issueKey.isBlank()) {
            throw new McpToolException("'issue_key' parameter is required");
        }

        String fields = (String) args.getOrDefault("fields",
                "summary,status,assignee,reporter,priority,issuetype,created,updated,description,comment");
        String expand = (String) args.get("expand");

        String queryParams = "?fields=" + URLEncoder.encode(fields, StandardCharsets.UTF_8);
        if (expand != null && !expand.isBlank()) {
            queryParams += "&expand=" + URLEncoder.encode(expand, StandardCharsets.UTF_8);
        }

        return client.get("/rest/api/2/issue/" + issueKey + queryParams, authHeader);
    }
}
