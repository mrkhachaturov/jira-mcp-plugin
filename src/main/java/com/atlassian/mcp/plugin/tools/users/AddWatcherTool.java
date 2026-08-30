package com.atlassian.mcp.plugin.tools.users;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AddWatcherTool extends TypedTool<AddWatcherTool.Args> {

  public record Args(
      @ToolArg(value = "Jira issue key (e.g., 'PROJ-123')", required = true) String issueKey,
      @ToolArg(value = "Jira username of the person to add as a watcher", required = true)
          String userIdentifier) {}

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public AddWatcherTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "add_watcher";
  }

  @Override
  public String description() {
    return "Add a user as a watcher to a Jira issue.";
  }

  @Override
  public boolean isWriteTool() {
    return true;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    String body;
    try {
      // The watchers endpoint takes the bare user name as a JSON string, not an object; any
      // wrapper property is rejected as an unrecognized field.
      body = mapper.writeValueAsString(args.userIdentifier());
    } catch (JsonProcessingException e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }
    return client.post(
        "/rest/api/2/issue/" + args.issueKey() + "/watchers", body, context.authHeader());
  }
}
