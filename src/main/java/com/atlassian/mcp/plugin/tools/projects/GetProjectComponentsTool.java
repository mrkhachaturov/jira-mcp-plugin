package com.atlassian.mcp.plugin.tools.projects;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;

public class GetProjectComponentsTool extends TypedTool<GetProjectComponentsTool.Args> {

  public record Args(
      @ToolArg(value = "Jira project key (e.g. 'PROJ', 'ACV2')", required = true)
          String projectKey) {}

  private final JiraRestClient client;

  public GetProjectComponentsTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "get_project_components";
  }

  @Override
  public String description() {
    return "Get all components for a specific Jira project.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    return client.get(
        "/rest/api/2/project/" + args.projectKey() + "/components", context.authHeader());
  }
}
