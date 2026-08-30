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

public class UpdateSprintTool extends DeclarativeTool {

  private static final ToolParam<String> SPRINT_ID =
      ToolParam.string("sprint_id", "The id of sprint (e.g., '10001')").required();
  private static final ToolParam<String> NAME =
      ToolParam.string("name", "(Optional) New name for the sprint");
  private static final ToolParam<String> STATE =
      ToolParam.string("state", "(Optional) New state for the sprint (future|active|closed)");
  private static final ToolParam<String> START_DATE =
      ToolParam.string("start_date", "(Optional) New start date for the sprint");
  private static final ToolParam<String> END_DATE =
      ToolParam.string("end_date", "(Optional) New end date for the sprint");
  private static final ToolParam<String> GOAL =
      ToolParam.string("goal", "(Optional) New goal for the sprint");

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public UpdateSprintTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "update_sprint";
  }

  @Override
  public String description() {
    return "Update jira sprint.";
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
    return List.of(SPRINT_ID, NAME, STATE, START_DATE, END_DATE, GOAL);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String sprintId = args.require(SPRINT_ID);
    String name = args.get(NAME);
    String state = args.get(STATE);
    String startDate = args.get(START_DATE);
    String endDate = args.get(END_DATE);
    String goal = args.get(GOAL);

    Map<String, Object> requestBody = new HashMap<>();
    if (name != null) requestBody.put("name", name);
    if (state != null) requestBody.put("state", state);
    if (startDate != null) requestBody.put("startDate", startDate);
    if (endDate != null) requestBody.put("endDate", endDate);
    if (goal != null) requestBody.put("goal", goal);
    try {
      String jsonBody = mapper.writeValueAsString(requestBody);
      return client.put("/rest/agile/1.0/sprint/" + sprintId, jsonBody, authHeader);
    } catch (Exception e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }
  }
}
