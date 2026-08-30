package com.atlassian.mcp.plugin.tools.boards;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddIssuesToSprintTool extends DeclarativeTool {

  private static final ToolParam<String> SPRINT_ID =
      ToolParam.string("sprint_id", "Sprint ID to add issues to").required();
  private static final ToolParam<String> ISSUE_KEYS =
      ToolParam.string("issue_keys", "Comma-separated issue keys (e.g., 'PROJ-1,PROJ-2')")
          .required();

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public AddIssuesToSprintTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "add_issues_to_sprint";
  }

  @Override
  public String description() {
    return "Add issues to a Jira sprint.";
  }

  @Override
  public boolean isWriteTool() {
    return true;
  }

  @Override
  public String requiredPluginKey() {
    return "com.atlassian.jira.plugins.jira-software-plugin";
  }

  @Override
  public List<ToolParam<?>> params() {
    return List.of(SPRINT_ID, ISSUE_KEYS);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String sprintId = args.require(SPRINT_ID);
    String issueKeys = args.require(ISSUE_KEYS);

    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("issue_keys", issueKeys);
    try {
      String jsonBody = mapper.writeValueAsString(requestBody);
      return client.post("/rest/agile/1.0/sprint/" + sprintId + "/issue", jsonBody, authHeader);
    } catch (Exception e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }
  }
}
