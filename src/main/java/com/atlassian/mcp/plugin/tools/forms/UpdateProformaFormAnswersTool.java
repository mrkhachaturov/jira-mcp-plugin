package com.atlassian.mcp.plugin.tools.forms;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UpdateProformaFormAnswersTool extends DeclarativeTool {

  private static final ToolParam<String> ISSUE_KEY =
      ToolParam.string("issue_key", "Jira issue key (e.g., 'PROJ-123')").required();
  private static final ToolParam<String> FORM_ID =
      ToolParam.string(
              "form_id", "ProForma form UUID (e.g., '1946b8b7-8f03-4dc0-ac2d-5fac0d960c6a')")
          .required();
  private static final ToolParam<String> ANSWERS =
      ToolParam.string(
              "answers",
              "List of answer objects. Each answer must have: questionId (string), type"
                  + " (TEXT/NUMBER/SELECT/etc), value (any)")
          .required();

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public UpdateProformaFormAnswersTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "update_proforma_form_answers";
  }

  @Override
  public String description() {
    return "Update form field answers using the Jira Forms REST API. This is the primary method for updating form data. Each answer object must specify the question ID, answer type, and value. **⚠️ KNOWN LIMITATION - DATETIME fields:** The Jira Forms API does NOT properly preserve time components in DATETIME fields. Only the date portion is stored; times are reset to midnight (00:00:00). **Workaround for DATETIME fields:** Use jira_update_issue to directly update the underlying custom fields instead: 1. Get the custom field ID from the form details (question's \"jiraField\" property) 2. Use jira_update_issue with fields like: {\"customfield_XXXXX\": \"2026-01-09T11:50:00-08:00\"} Example: ```python # Instead of updating via form (loses time): # jira_update_proforma_form_answers(issue_key, form_id, [{\"questionId\": \"91\", \"type\": \"DATETIME\", \"value\": \"...\"}]) # Use direct field update (preserves time): jira_update_issue(issue_key, {\"customfield_10542\": \"2026-01-09T11:50:00-08:00\"}) ``` **Automatic DateTime Conversion:** For DATE and DATETIME fields, you can provide values as: - ISO 8601 strings (e.g., \"2024-12-17T19:00:00Z\", \"2024-12-17\") - Unix timestamps in milliseconds (e.g., 1734465600000) The tool automatically converts ISO 8601 strings to Unix timestamps. Example answers: [ {\"questionId\": \"q1\", \"type\": \"TEXT\", \"value\": \"Updated description\"}, {\"questionId\": \"q2\", \"type\": \"SELECT\", \"value\": \"Product A\"}, {\"questionId\": \"q3\", \"type\": \"NUMBER\", \"value\": 42}, {\"questionId\": \"q4\", \"type\": \"DATE\", \"value\": \"2024-12-17\"} ] Common answer types: - TEXT: String values - NUMBER: Numeric values - DATE: Date values (ISO 8601 string or Unix timestamp in ms) - DATETIME: DateTime values - ⚠️ USE WORKAROUND ABOVE - SELECT: Single selection from options - MULTI_SELECT: Multiple selections (value as list) - CHECKBOX: Boolean values";
  }

  @Override
  public boolean isWriteTool() {
    return true;
  }

  @Override
  public String requiredPluginKey() {
    return "com.atlassian.jira.plugins.jira-proforma-plugin";
  }

  @Override
  public List<ToolParam<?>> params() {
    return List.of(ISSUE_KEY, FORM_ID, ANSWERS);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String issueKey = args.require(ISSUE_KEY);
    String formId = args.require(FORM_ID);
    String answers = args.require(ANSWERS);

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("form_id", formId);
    requestBody.put("answers", answers);
    try {
      String jsonBody = mapper.writeValueAsString(requestBody);
      return client.put(
          "/rest/api/2/issue/" + issueKey + "/properties/proforma.forms", jsonBody, authHeader);
    } catch (Exception e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }
  }
}
