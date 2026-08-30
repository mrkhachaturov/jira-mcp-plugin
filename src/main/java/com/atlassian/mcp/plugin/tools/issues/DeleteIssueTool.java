package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;

public class DeleteIssueTool extends TypedTool<DeleteIssueTool.Args> {

  public record Args(
      @ToolArg(value = "Jira issue key (e.g. 'PROJ-123', 'ACV2-642')", required = true)
          String issueKey) {}

  private final JiraRestClient client;

  public DeleteIssueTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "delete_issue";
  }

  @Override
  public String description() {
    return "Delete an existing Jira issue.";
  }

  @Override
  public boolean isWriteTool() {
    return true;
  }

  @Override
  public boolean isDestructiveTool() {
    return true;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    return client.delete("/rest/api/2/issue/" + args.issueKey(), context.authHeader());
  }
}
