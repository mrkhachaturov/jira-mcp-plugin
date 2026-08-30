package com.atlassian.mcp.plugin.tools.transitions;

import com.atlassian.mcp.plugin.JiraMarkupConverter;
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransitionIssueTool extends DeclarativeTool {

  private static final ToolParam<String> ISSUE_KEY =
      ToolParam.string("issue_key", "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')").required();
  private static final ToolParam<String> TRANSITION_ID =
      ToolParam.string(
              "transition_id",
              "ID of the transition to perform. Use the jira_get_transitions tool first to get the"
                  + " available transition IDs for the issue. Example values: '11', '21', '31'")
          .required();
  private static final ToolParam<String> FIELDS =
      ToolParam.string(
          "fields",
          "(Optional) JSON string of fields to update during the transition. Some transitions"
              + " require specific fields to be set (e.g., resolution). Example: '{\"resolution\":"
              + " {\"name\": \"Fixed\"}}'");
  private static final ToolParam<String> COMMENT =
      ToolParam.string(
          "comment",
          "(Optional) Comment to add during the transition in Markdown format. This will be"
              + " visible in the issue history.");

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public TransitionIssueTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "transition_issue";
  }

  @Override
  public String description() {
    return "Transition a Jira issue to a new status.";
  }

  @Override
  public boolean isWriteTool() {
    return true;
  }

  @Override
  public List<ToolParam<?>> params() {
    return List.of(ISSUE_KEY, TRANSITION_ID, FIELDS, COMMENT);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String issueKey = args.require(ISSUE_KEY);
    String transitionId = args.require(TRANSITION_ID);
    String fieldsJson = args.get(FIELDS);
    String comment = args.get(COMMENT);

    // Jira API expects: {"transition": {"id": "..."}, "fields": {...}, "update": {...}}
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("transition", Map.of("id", transitionId));

    if (fieldsJson != null) {
      try {
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = mapper.readValue(fieldsJson, Map.class);
        requestBody.put("fields", fields);
      } catch (Exception e) {
        throw new McpToolException("Invalid fields JSON: " + e.getMessage());
      }
    }

    if (comment != null) {
      requestBody.put(
          "update",
          Map.of(
              "comment",
              List.of(Map.of("add", Map.of("body", JiraMarkupConverter.markdownToJira(comment))))));
    }

    try {
      String jsonBody = mapper.writeValueAsString(requestBody);
      client.post("/rest/api/2/issue/" + issueKey + "/transitions", jsonBody, authHeader);
      // The transition response body is empty (204), so re-read the issue to hand back its new
      // state.
      return client.get("/rest/api/2/issue/" + issueKey, authHeader);
    } catch (McpToolException e) {
      throw e;
    } catch (Exception e) {
      throw new McpToolException("Failed to transition issue: " + e.getMessage());
    }
  }
}
