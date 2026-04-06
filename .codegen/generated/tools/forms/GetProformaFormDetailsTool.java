package com.atlassian.mcp.plugin.tools.forms;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.util.List;
import java.util.Map;

public class GetProformaFormDetailsTool implements McpTool {
    private final JiraRestClient client;

    public GetProformaFormDetailsTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_proforma_form_details"; }

    @Override
    public String description() {
        return "Get detailed information about a specific ProForma form. Uses the new Jira Forms REST API. Returns form details including ADF design structure.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123')"),
                        "form_id", Map.of("type", "string", "description", "ProForma form UUID (e.g., '1946b8b7-8f03-4dc0-ac2d-5fac0d960c6a')")
                ),
                "required", List.of("issue_key", "form_id")
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
        String formId = (String) args.get("form_id");
        if (formId == null || formId.isBlank()) {
            throw new McpToolException("'form_id' parameter is required");
        }

        return client.get("/rest/api/2/issue/" + issueKey + "/properties/proforma.forms", authHeader);
    }
}
