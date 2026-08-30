package com.atlassian.mcp.plugin.tools.boards;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

public class AddIssuesToSprintTool extends TypedTool<AddIssuesToSprintTool.Args> {

  public record Args(
      @ToolArg(value = "The id of the sprint to add issues to (e.g. 10001)", required = true)
          long sprintId,
      @ToolArg(
              value = "Issue keys to move into the sprint, e.g. ['PROJ-1', 'PROJ-2']",
              required = true)
          List<String> issueKeys) {}

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public AddIssuesToSprintTool(JiraRestClient client) {
    super(Args.class);
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
  protected String run(Args args, McpContext context) throws McpToolException {
    if (args.issueKeys().isEmpty()) {
      throw new McpToolException("'issue_keys' must name at least one issue");
    }

    String body;
    try {
      body = mapper.writeValueAsString(Map.of("issues", args.issueKeys()));
    } catch (JsonProcessingException e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }
    return client.post(
        "/rest/agile/1.0/sprint/" + args.sprintId() + "/issue", body, context.authHeader());
  }
}
