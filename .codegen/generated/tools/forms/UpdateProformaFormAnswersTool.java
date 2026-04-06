package com.atlassian.mcp.plugin.tools.forms;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpdateProformaFormAnswersTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public UpdateProformaFormAnswersTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "update_proforma_form_answers"; }

    @Override
    public String description() {
        return "Update form field answers using the Jira Forms REST API. This is the primary method for updating form data. Each answer object must specify the question ID, answer type, and value. **⚠️ KNOWN LIMITATION - DATETIME fields:** The Jira Forms API does NOT properly preserve time components in DATETIME fields. Only the date portion is stored; times are reset to midnight (00:00:00). **Workaround for DATETIME fields:** Use jira_update_issue to directly update the underlying custom fields instead: 1. Get the custom field ID from the form details (question's \"jiraField\" property) 2. Use jira_update_issue with fields like: {\"customfield_XXXXX\": \"2026-01-09T11:50:00-08:00\"} Example: ```python # Instead of updating via form (loses time): # jira_update_proforma_form_answers(issue_key, form_id, [{\"questionId\": \"91\", \"type\": \"DATETIME\", \"value\": \"...\"}]) # Use direct field update (preserves time): jira_update_issue(issue_key, {\"customfield_10542\": \"2026-01-09T11:50:00-08:00\"}) ``` **Automatic DateTime Conversion:** For DATE and DATETIME fields, you can provide values as: - ISO 8601 strings (e.g., \"2024-12-17T19:00:00Z\", \"2024-12-17\") - Unix timestamps in milliseconds (e.g., 1734465600000) The tool automatically converts ISO 8601 strings to Unix timestamps. Example answers: [ {\"questionId\": \"q1\", \"type\": \"TEXT\", \"value\": \"Updated description\"}, {\"questionId\": \"q2\", \"type\": \"SELECT\", \"value\": \"Product A\"}, {\"questionId\": \"q3\", \"type\": \"NUMBER\", \"value\": 42}, {\"questionId\": \"q4\", \"type\": \"DATE\", \"value\": \"2024-12-17\"} ] Common answer types: - TEXT: String values - NUMBER: Numeric values - DATE: Date values (ISO 8601 string or Unix timestamp in ms) - DATETIME: DateTime values - ⚠️ USE WORKAROUND ABOVE - SELECT: Single selection from options - MULTI_SELECT: Multiple selections (value as list) - CHECKBOX: Boolean values";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123')"),
                        "form_id", Map.of("type", "string", "description", "ProForma form UUID (e.g., '1946b8b7-8f03-4dc0-ac2d-5fac0d960c6a')"),
                        "answers", Map.of("type", "string", "description", "List of answer objects. Each answer must have: questionId (string), type (TEXT/NUMBER/SELECT/etc), value (any)")
                ),
                "required", List.of("issue_key", "form_id", "answers")
        );
    }

    @Override public boolean isWriteTool() { return true; }

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
        String answers = (String) args.get("answers");
        if (answers == null || answers.isBlank()) {
            throw new McpToolException("'answers' parameter is required");
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("form_id", formId);
        requestBody.put("answers", answers);
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            return client.put("/rest/api/2/issue/" + issueKey + "/properties/proforma.forms", jsonBody, authHeader);
        } catch (Exception e) {
            throw new McpToolException("Failed to serialize request: " + e.getMessage());
        }
    }
}
