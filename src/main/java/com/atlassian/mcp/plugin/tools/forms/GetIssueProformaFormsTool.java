package com.atlassian.mcp.plugin.tools.forms;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class GetIssueProformaFormsTool implements McpTool {
    private final JiraRestClient client;
    public GetIssueProformaFormsTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "get_issue_proforma_forms"; }
    @Override public String description() { return "Get Proforma forms attached to a Jira issue."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "issue_key", Map.of("type", "string", "description", "Jira issue key")),
                "required", List.of("issue_key"));
    }
    @Override public boolean isWriteTool() { return false; }
    @Override public String requiredPluginKey() { return "com.atlassian.proforma"; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String ik = (String) args.get("issue_key");
        if (ik == null) throw new McpToolException("issue_key is required");
        return client.get("/rest/proforma/1/issue/" + ik + "/form", authHeader);
    }
}
