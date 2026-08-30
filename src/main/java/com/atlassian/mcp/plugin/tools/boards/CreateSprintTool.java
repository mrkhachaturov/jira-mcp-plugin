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

public class CreateSprintTool extends DeclarativeTool {

  private static final ToolParam<Integer> BOARD_ID =
      ToolParam.integer("board_id", "The id of board (e.g., '1000')").required();
  private static final ToolParam<String> NAME =
      ToolParam.string("name", "Name of the sprint (e.g., 'Sprint 1')").required();
  private static final ToolParam<String> START_DATE =
      ToolParam.string("start_date", "Start time for sprint (ISO 8601 format)").required();
  private static final ToolParam<String> END_DATE =
      ToolParam.string("end_date", "End time for sprint (ISO 8601 format)").required();
  private static final ToolParam<String> GOAL =
      ToolParam.string("goal", "(Optional) Goal of the sprint");

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public CreateSprintTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "create_sprint";
  }

  @Override
  public String description() {
    return "Create Jira sprint for a board.";
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
    return List.of(BOARD_ID, NAME, START_DATE, END_DATE, GOAL);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    int boardId = args.require(BOARD_ID);
    String name = args.require(NAME);
    String startDate = args.require(START_DATE);
    String endDate = args.require(END_DATE);
    String goal = args.get(GOAL);

    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("originBoardId", boardId);
    requestBody.put("name", name);
    requestBody.put("startDate", startDate);
    requestBody.put("endDate", endDate);
    if (goal != null) requestBody.put("goal", goal);
    try {
      String jsonBody = mapper.writeValueAsString(requestBody);
      return client.post("/rest/agile/1.0/sprint", jsonBody, authHeader);
    } catch (Exception e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }
  }
}
