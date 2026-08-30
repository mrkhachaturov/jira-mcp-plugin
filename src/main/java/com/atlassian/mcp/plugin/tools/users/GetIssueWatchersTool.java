package com.atlassian.mcp.plugin.tools.users;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import java.util.List;

public class GetIssueWatchersTool extends DeclarativeTool {

  private static final ToolParam<String> ISSUE_KEY =
      ToolParam.string("issue_key", "Jira issue key (e.g., 'PROJ-123')").required();

  private final JiraRestClient client;

  public GetIssueWatchersTool(JiraRestClient client) {
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
  public List<ToolParam<?>> params() {
    return List.of(ISSUE_KEY);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String issueKey = args.require(ISSUE_KEY);

    return client.get("/rest/api/2/issue/" + issueKey + "/watchers", authHeader);
  }
}
