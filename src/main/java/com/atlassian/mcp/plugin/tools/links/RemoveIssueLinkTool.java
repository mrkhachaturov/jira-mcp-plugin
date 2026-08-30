package com.atlassian.mcp.plugin.tools.links;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;

public class RemoveIssueLinkTool extends TypedTool<RemoveIssueLinkTool.Args> {

  public record Args(
      @ToolArg(value = "The id of the link to remove (e.g. 10042)", required = true) long linkId) {}

  private final JiraRestClient client;

  public RemoveIssueLinkTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "remove_issue_link";
  }

  @Override
  public String description() {
    return "Remove a link between two Jira issues.";
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
    return client.delete("/rest/api/2/issueLink/" + args.linkId(), context.authHeader());
  }
}
