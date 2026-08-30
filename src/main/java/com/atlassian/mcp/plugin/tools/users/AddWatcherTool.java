package com.atlassian.mcp.plugin.tools.users;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

public class AddWatcherTool extends DeclarativeTool {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final ToolParam<String> ISSUE_KEY =
      ToolParam.string("issue_key", "Jira issue key (e.g., 'PROJ-123')").required();
  private static final ToolParam<String> USER_IDENTIFIER =
      ToolParam.string(
              "user_identifier",
              "User to add as watcher. For Jira Cloud, use the account ID. For Jira Server/DC, use"
                  + " the username.")
          .required();

  private final JiraRestClient client;

  public AddWatcherTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "add_watcher";
  }

  @Override
  public String description() {
    return "Add a user as a watcher to a Jira issue.";
  }

  @Override
  public boolean isWriteTool() {
    return true;
  }

  @Override
  public List<ToolParam<?>> params() {
    return List.of(ISSUE_KEY, USER_IDENTIFIER);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String issueKey = args.require(ISSUE_KEY);
    String userIdentifier = args.require(USER_IDENTIFIER);

    try {
      // The watchers endpoint takes the bare user name as a JSON string, not an object; any
      // wrapper property is rejected as an unrecognized field.
      String body = MAPPER.writeValueAsString(userIdentifier);
      return client.post("/rest/api/2/issue/" + issueKey + "/watchers", body, authHeader);
    } catch (McpToolException e) {
      throw e;
    } catch (Exception e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }
  }
}
