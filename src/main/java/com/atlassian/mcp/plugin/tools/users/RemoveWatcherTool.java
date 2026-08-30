package com.atlassian.mcp.plugin.tools.users;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class RemoveWatcherTool extends TypedTool<RemoveWatcherTool.Args> {

  public record Args(
      @ToolArg(value = "Jira issue key (e.g., 'PROJ-123')", required = true) String issueKey,
      @ToolArg(value = "Jira username of the watcher to remove", required = true)
          String userIdentifier) {}

  private final JiraRestClient client;

  public RemoveWatcherTool(JiraRestClient client) {
    super(Args.class);
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
  protected String run(Args args, McpContext context) throws McpToolException {
    return client.delete(
        "/rest/api/2/issue/"
            + args.issueKey()
            + "/watchers?username="
            + encode(args.userIdentifier()),
        context.authHeader());
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
