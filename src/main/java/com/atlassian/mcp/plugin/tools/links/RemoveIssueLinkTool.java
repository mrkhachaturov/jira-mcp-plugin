package com.atlassian.mcp.plugin.tools.links;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import java.util.List;

public class RemoveIssueLinkTool extends DeclarativeTool {

  private static final ToolParam<String> LINK_ID =
      ToolParam.string("link_id", "The ID of the link to remove").required();

  private final JiraRestClient client;

  public RemoveIssueLinkTool(JiraRestClient client) {
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
  public List<ToolParam<?>> params() {
    return List.of(LINK_ID);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    return client.delete("/rest/api/2/issueLink/" + args.require(LINK_ID), authHeader);
  }
}
