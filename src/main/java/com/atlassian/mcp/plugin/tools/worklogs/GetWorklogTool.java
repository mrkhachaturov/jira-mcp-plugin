package com.atlassian.mcp.plugin.tools.worklogs;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;

public class GetWorklogTool extends TypedTool<GetWorklogTool.Args> {

  public record Args(
      @ToolArg(value = "Jira issue key (e.g. 'PROJ-123', 'ACV2-642')", required = true)
          String issueKey) {}

  private final JiraRestClient client;

  public GetWorklogTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "get_worklog";
  }

  @Override
  public String description() {
    return "Get worklog entries for a Jira issue.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    return client.get("/rest/api/2/issue/" + args.issueKey() + "/worklog", context.authHeader());
  }
}
