package com.atlassian.mcp.plugin.tools.projects;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import java.util.List;

public class GetAllProjectsTool extends DeclarativeTool {

  private static final ToolParam<Boolean> INCLUDE_ARCHIVED =
      ToolParam.bool("include_archived", "Whether to include archived projects in the results")
          .withDefault(false);

  private final JiraRestClient client;

  public GetAllProjectsTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "get_all_projects";
  }

  @Override
  public String description() {
    return "Get all Jira projects accessible to the current user.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  public List<ToolParam<?>> params() {
    return List.of(INCLUDE_ARCHIVED);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    boolean includeArchived = args.get(INCLUDE_ARCHIVED);

    return client.get("/rest/api/2/project?includeArchived=" + includeArchived, authHeader);
  }
}
