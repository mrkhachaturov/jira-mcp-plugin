package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.JiraMarkupConverter;
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CreateIssueTool extends DeclarativeTool {

  private static final ToolParam<String> PROJECT_KEY =
      ToolParam.string(
              "project_key",
              "The JIRA project key (e.g. 'PROJ', 'DEV', 'ACV2'). This is the prefix of issue keys"
                  + " in your project. Never assume what it might be, always ask the user.")
          .required();
  private static final ToolParam<String> SUMMARY =
      ToolParam.string("summary", "Summary/title of the issue").required();
  private static final ToolParam<String> ISSUE_TYPE =
      ToolParam.string(
              "issue_type",
              "Issue type (e.g. 'Task', 'Bug', 'Story', 'Epic', 'Subtask'). The available types"
                  + " depend on your project configuration. For subtasks, use 'Subtask' (not"
                  + " 'Sub-task') and include parent in additional_fields.")
          .required();
  private static final ToolParam<String> ASSIGNEE =
      ToolParam.string(
          "assignee",
          "(Optional) Assignee's user identifier (string): Email, display name, or account ID"
              + " (e.g., 'user@example.com', 'John Doe', 'accountid:...')");
  private static final ToolParam<String> DESCRIPTION =
      ToolParam.string("description", "Issue description in Markdown format");
  private static final ToolParam<String> COMPONENTS =
      ToolParam.string(
          "components",
          "(Optional) Comma-separated list of component names to assign (e.g., 'Frontend,API')");
  private static final ToolParam<String> ADDITIONAL_FIELDS =
      ToolParam.string(
          "additional_fields",
          "(Optional) JSON string of additional fields to set. Examples: - Set priority:"
              + " {\"priority\": {\"name\": \"High\"}} - Add labels: {\"labels\": [\"frontend\","
              + " \"urgent\"]} - Link to parent (for any issue type): {\"parent\": \"PROJ-123\"} -"
              + " Link to epic: {\"epicKey\": \"EPIC-123\"} or {\"epic_link\": \"EPIC-123\"} - Set"
              + " Fix Version/s: {\"fixVersions\": [{\"id\": \"10020\"}]} - Custom fields:"
              + " {\"customfield_10010\": \"value\"}");

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public CreateIssueTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "create_issue";
  }

  @Override
  public String description() {
    return "Create a new Jira issue with optional Epic link or parent for subtasks.";
  }

  @Override
  public boolean isWriteTool() {
    return true;
  }

  @Override
  public List<ToolParam<?>> params() {
    return List.of(
        PROJECT_KEY, SUMMARY, ISSUE_TYPE, ASSIGNEE, DESCRIPTION, COMPONENTS, ADDITIONAL_FIELDS);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String projectKey = args.require(PROJECT_KEY);
    String summary = args.require(SUMMARY);
    String issueType = args.require(ISSUE_TYPE);
    String assignee = args.get(ASSIGNEE);
    String description = args.get(DESCRIPTION);
    String components = args.get(COMPONENTS);
    String additionalFields = args.get(ADDITIONAL_FIELDS);

    Map<String, Object> fields = new HashMap<>();
    fields.put("project", Map.of("key", projectKey));
    fields.put("summary", summary);
    fields.put("issuetype", Map.of("name", issueType));

    if (description != null) {
      fields.put("description", JiraMarkupConverter.markdownToJira(description));
    }
    if (assignee != null) fields.put("assignee", Map.of("name", assignee));
    if (components != null) {
      fields.put(
          "components",
          Arrays.stream(components.split(","))
              .map(String::trim)
              .filter(s -> !s.isEmpty())
              .map(c -> Map.of("name", c))
              .collect(Collectors.toList()));
    }

    if (additionalFields != null && !additionalFields.isBlank()) {
      try {
        @SuppressWarnings("unchecked")
        Map<String, Object> extra = mapper.readValue(additionalFields, Map.class);
        fields.putAll(extra);
      } catch (Exception e) {
        throw new McpToolException("Invalid additional_fields JSON: " + e.getMessage());
      }
    }

    try {
      String jsonBody = mapper.writeValueAsString(Map.of("fields", fields));
      String createResponse = client.post("/rest/api/2/issue", jsonBody, authHeader);
      JsonNode created = mapper.readTree(createResponse);
      String newKey = created.path("key").asText(null);
      if (newKey != null) {
        // The create response carries only id/key/self, so re-read the issue for the full payload.
        String fullIssue = client.get("/rest/api/2/issue/" + newKey, authHeader);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("issue", mapper.readTree(fullIssue));
        return mapper.writeValueAsString(result);
      }
      return createResponse;
    } catch (McpToolException e) {
      throw e;
    } catch (Exception e) {
      throw new McpToolException("Failed to create issue: " + e.getMessage());
    }
  }
}
