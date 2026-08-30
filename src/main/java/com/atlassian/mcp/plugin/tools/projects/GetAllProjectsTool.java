package com.atlassian.mcp.plugin.tools.projects;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;

public class GetAllProjectsTool extends TypedTool<GetAllProjectsTool.Args> {

  public record Args(
      @ToolArg(
              value = "Whether to include archived projects in the results; false by default",
              defaultValue = "false")
          boolean includeArchived) {}

  private final JiraRestClient client;

  public GetAllProjectsTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "get_all_projects";
  }

  @Override
  public String description() {
    return "Get all Jira projects accessible to the current user.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    return client.get(
        "/rest/api/2/project?includeArchived=" + args.includeArchived(), context.authHeader());
  }
}
