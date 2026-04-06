package com.atlassian.mcp.plugin.tools.forms;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class GetProformaFormDetailsTool implements McpTool {
    private final JiraRestClient client;
    public GetProformaFormDetailsTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "get_proforma_form_details"; }
    @Override public String description() { return "Get details of a specific Proforma form on a Jira issue."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "issue_key", Map.of("type", "string", "description", "Jira issue key"),
                "form_id", Map.of("type", "string", "description", "Form ID")),
                "required", List.of("issue_key", "form_id"));
    }
    @Override public boolean isWriteTool() { return false; }
    @Override public String requiredPluginKey() { return "com.atlassian.proforma"; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String ik = (String) args.get("issue_key");
        String fid = (String) args.get("form_id");
        if (ik == null || fid == null) throw new McpToolException("issue_key and form_id required");
        return client.get("/rest/proforma/1/issue/" + ik + "/form/" + fid, authHeader);
    }
}
