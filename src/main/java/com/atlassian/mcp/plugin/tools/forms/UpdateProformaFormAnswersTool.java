package com.atlassian.mcp.plugin.tools.forms;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class UpdateProformaFormAnswersTool implements McpTool {
    private final JiraRestClient client;
    public UpdateProformaFormAnswersTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "update_proforma_form_answers"; }
    @Override public String description() { return "Update answers on a Proforma form."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "issue_key", Map.of("type", "string", "description", "Jira issue key"),
                "form_id", Map.of("type", "string", "description", "Form ID"),
                "answers_json", Map.of("type", "string", "description", "JSON object of form answers")),
                "required", List.of("issue_key", "form_id", "answers_json"));
    }
    @Override public boolean isWriteTool() { return true; }
    @Override public String requiredPluginKey() { return "com.atlassian.proforma"; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String ik = (String) args.get("issue_key");
        String fid = (String) args.get("form_id");
        String answers = (String) args.get("answers_json");
        if (ik == null || fid == null || answers == null) throw new McpToolException("issue_key, form_id, answers_json required");
        return client.put("/rest/proforma/1/issue/" + ik + "/form/" + fid, answers, authHeader);
    }
}
