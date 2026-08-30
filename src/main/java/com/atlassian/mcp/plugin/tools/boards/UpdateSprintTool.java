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

public class UpdateSprintTool extends TypedTool<UpdateSprintTool.Args> {

  public record Args(
      @ToolArg(value = "The id of the sprint (e.g. 10001)", required = true) long sprintId,
      @ToolArg("(Optional) New name for the sprint") String name,
      @ToolArg(
              value = "(Optional) New state for the sprint",
              allowed = {"future", "active", "closed"})
          String state,
      @ToolArg("(Optional) New start date for the sprint (ISO 8601 format)") String startDate,
      @ToolArg("(Optional) New end date for the sprint (ISO 8601 format)") String endDate,
      @ToolArg("(Optional) New goal for the sprint") String goal) {}

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public UpdateSprintTool(JiraRestClient client) {
    super(Args.class);
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
  protected String run(Args args, McpContext context) throws McpToolException {
    Map<String, Object> requestBody = new LinkedHashMap<>();
    if (args.name() != null) requestBody.put("name", args.name());
    if (args.state() != null) requestBody.put("state", args.state());
    if (args.startDate() != null) requestBody.put("startDate", args.startDate());
    if (args.endDate() != null) requestBody.put("endDate", args.endDate());
    if (args.goal() != null) requestBody.put("goal", args.goal());

    String body;
    try {
      body = mapper.writeValueAsString(requestBody);
    } catch (JsonProcessingException e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }
    return client.put("/rest/agile/1.0/sprint/" + args.sprintId(), body, context.authHeader());
  }
}
