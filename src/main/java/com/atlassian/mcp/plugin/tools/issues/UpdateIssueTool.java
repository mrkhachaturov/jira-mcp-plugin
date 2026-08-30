package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.JiraMarkupConverter;
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UpdateIssueTool extends DeclarativeTool {

  private static final ToolParam<String> ISSUE_KEY =
      ToolParam.string("issue_key", "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')").required();
  private static final ToolParam<String> FIELDS =
      ToolParam.string(
              "fields",
              "JSON string of fields to update. For 'assignee', provide the username. For"
                  + " 'description', provide text in Markdown format."
                  + " Example: '{\"assignee\": \"user@example.com\", \"summary\": \"New Summary\","
                  + " \"description\": \"## Updated\\nMarkdown text\"}'")
          .required();
  private static final ToolParam<String> ADDITIONAL_FIELDS =
      ToolParam.string(
          "additional_fields",
          "(Optional) JSON string of additional fields to update. Use this for custom fields or"
              + " more complex updates. Link to epic: {\"epicKey\": \"EPIC-123\"} or"
              + " {\"epic_link\": \"EPIC-123\"}.");
  private static final ToolParam<String> COMPONENTS =
      ToolParam.string(
          "components",
          "(Optional) Comma-separated list of component names (e.g., 'Frontend,API')");

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public UpdateIssueTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "update_issue";
  }

  @Override
  public String description() {
    return "Update an existing Jira issue including changing status, adding Epic links, updating fields, etc.";
  }

  @Override
  public boolean isWriteTool() {
    return true;
  }

  @Override
  public List<ToolParam<?>> params() {
    return List.of(ISSUE_KEY, FIELDS, ADDITIONAL_FIELDS, COMPONENTS);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String issueKey = args.require(ISSUE_KEY);
    String fields = args.require(FIELDS);
    String additionalFields = args.get(ADDITIONAL_FIELDS);
    String components = args.get(COMPONENTS);

    Map<String, Object> updateFields;
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> parsed = mapper.readValue(fields, Map.class);
      updateFields = new HashMap<>(parsed);
    } catch (Exception e) {
      throw new McpToolException("Invalid fields JSON: " + e.getMessage());
    }

    if (additionalFields != null && !additionalFields.isBlank()) {
      try {
        @SuppressWarnings("unchecked")
        Map<String, Object> extra = mapper.readValue(additionalFields, Map.class);
        updateFields.putAll(extra);
      } catch (Exception e) {
        throw new McpToolException("Invalid additional_fields JSON: " + e.getMessage());
      }
    }

    if (updateFields.containsKey("description")
        && updateFields.get("description") instanceof String desc) {
      updateFields.put("description", JiraMarkupConverter.markdownToJira(desc));
    }

    if (components != null && !components.isBlank()) {
      updateFields.put(
          "components",
          Arrays.stream(components.split(","))
              .map(String::trim)
              .filter(s -> !s.isEmpty())
              .map(c -> Map.of("name", c))
              .collect(Collectors.toList()));
    }

    try {
      String jsonBody = mapper.writeValueAsString(Map.of("fields", updateFields));
      client.put("/rest/api/2/issue/" + issueKey, jsonBody, authHeader);
      // Jira's PUT returns 204 with no body, so the updated issue has to be re-read.
      String updatedIssue = client.get("/rest/api/2/issue/" + issueKey, authHeader);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("success", true);
      result.put("issue", mapper.readTree(updatedIssue));
      return mapper.writeValueAsString(result);
    } catch (McpToolException e) {
      throw e;
    } catch (Exception e) {
      throw new McpToolException("Failed to update issue: " + e.getMessage());
    }
  }
}
