package com.atlassian.mcp.plugin.tools.boards;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

public class CreateSprintTool extends TypedTool<CreateSprintTool.Args> {

  public record Args(
      @ToolArg(value = "The id of the board the sprint belongs to (e.g. 1000)", required = true)
          long boardId,
      @ToolArg(value = "Name of the sprint (e.g. 'Sprint 1')", required = true) String name,
      @ToolArg(value = "Start time for the sprint (ISO 8601 format)", required = true)
          String startDate,
      @ToolArg(value = "End time for the sprint (ISO 8601 format)", required = true) String endDate,
      @ToolArg("(Optional) Goal of the sprint") String goal) {}

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public CreateSprintTool(JiraRestClient client) {
    super(Args.class);
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
  protected String run(Args args, McpContext context) throws McpToolException {
    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("originBoardId", args.boardId());
    requestBody.put("name", args.name());
    requestBody.put("startDate", args.startDate());
    requestBody.put("endDate", args.endDate());
    if (args.goal() != null) requestBody.put("goal", args.goal());

    String body;
    try {
      body = mapper.writeValueAsString(requestBody);
    } catch (JsonProcessingException e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }
    return client.post("/rest/agile/1.0/sprint", body, context.authHeader());
  }
}
