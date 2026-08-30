package com.atlassian.mcp.plugin.tools.users;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class GetUserProfileTool extends DeclarativeTool {

  private static final ToolParam<String> USER_IDENTIFIER =
      ToolParam.string(
              "user_identifier",
              "Identifier for the user (e.g., email address 'user@example.com', username"
                  + " 'johndoe', account ID 'accountid:...', or key for Server/DC).")
          .required();

  private final JiraRestClient client;

  public GetUserProfileTool(JiraRestClient client) {
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
  public List<ToolParam<?>> params() {
    return List.of(USER_IDENTIFIER);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String userIdentifier = args.require(USER_IDENTIFIER);

    return client.get("/rest/api/2/user?username=" + encode(userIdentifier), authHeader);
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
