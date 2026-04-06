package com.atlassian.mcp.plugin.tools.forms;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.util.List;
import java.util.Map;

public class GetIssueProformaFormsTool implements McpTool {
    private final JiraRestClient client;

    public GetIssueProformaFormsTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_issue_proforma_forms"; }

    @Override
    public String description() {
        return "Get all ProForma forms associated with a Jira issue. Uses the new Jira Forms REST API. Form IDs are returned as UUIDs.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123')")
                ),
                "required", List.of("issue_key")
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String requiredPluginKey() { return "com.atlassian.jira.plugins.jira-proforma-plugin"; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        if (issueKey == null || issueKey.isBlank()) {
            throw new McpToolException("'issue_key' parameter is required");
        }

        return client.get("/rest/api/2/issue/" + issueKey + "/properties/proforma.forms", authHeader);
    }
}
