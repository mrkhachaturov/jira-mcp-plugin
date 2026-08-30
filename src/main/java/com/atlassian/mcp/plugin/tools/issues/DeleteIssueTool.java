package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import java.util.List;

public class DeleteIssueTool extends DeclarativeTool {

  private static final ToolParam<String> ISSUE_KEY =
      ToolParam.string("issue_key", "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')").required();

  private final JiraRestClient client;

  public DeleteIssueTool(JiraRestClient client) {
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
  public List<ToolParam<?>> params() {
    return List.of(ISSUE_KEY);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    return client.delete("/rest/api/2/issue/" + args.require(ISSUE_KEY), authHeader);
  }
}
