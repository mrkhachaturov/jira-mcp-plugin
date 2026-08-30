package com.atlassian.mcp.plugin.tools.users;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;

public class GetIssueWatchersTool extends TypedTool<GetIssueWatchersTool.Args> {

  public record Args(
      @ToolArg(value = "Jira issue key (e.g., 'PROJ-123')", required = true) String issueKey) {}

  private final JiraRestClient client;

  public GetIssueWatchersTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "get_issue_watchers";
  }

  @Override
  public String description() {
    return "Get the list of watchers for a Jira issue.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    return client.get("/rest/api/2/issue/" + args.issueKey() + "/watchers", context.authHeader());
  }
}
