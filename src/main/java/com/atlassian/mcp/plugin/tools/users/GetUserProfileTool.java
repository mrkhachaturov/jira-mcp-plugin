package com.atlassian.mcp.plugin.tools.users;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class GetUserProfileTool extends TypedTool<GetUserProfileTool.Args> {

  public record Args(
      @ToolArg(value = "Jira username of the user to look up (e.g., 'jsmith')", required = true)
          String userIdentifier) {}

  private final JiraRestClient client;

  public GetUserProfileTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "get_user_profile";
  }

  @Override
  public String description() {
    return "Retrieve profile information for a specific Jira user.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    return client.get(
        "/rest/api/2/user?username=" + encode(args.userIdentifier()), context.authHeader());
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
