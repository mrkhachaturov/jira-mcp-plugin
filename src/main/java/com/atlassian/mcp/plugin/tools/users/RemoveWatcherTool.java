package com.atlassian.mcp.plugin.tools.users;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class RemoveWatcherTool extends DeclarativeTool {

  private static final ToolParam<String> ISSUE_KEY =
      ToolParam.string("issue_key", "Jira issue key (e.g., 'PROJ-123')").required();
  private static final ToolParam<String> USERNAME =
      ToolParam.string("username", "Username of the watcher to remove.").required();

  private final JiraRestClient client;

  public RemoveWatcherTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "remove_watcher";
  }

  @Override
  public String description() {
    return "Remove a user from watching a Jira issue.";
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
    return List.of(ISSUE_KEY, USERNAME);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String issueKey = args.require(ISSUE_KEY);
    String username = args.require(USERNAME);

    return client.delete(
        "/rest/api/2/issue/" + issueKey + "/watchers?username=" + encode(username), authHeader);
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
