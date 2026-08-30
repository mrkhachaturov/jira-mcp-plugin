package com.atlassian.mcp.plugin.tools.projects;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import java.util.List;

public class GetProjectVersionsTool extends DeclarativeTool {

  private static final ToolParam<String> PROJECT_KEY =
      ToolParam.string("project_key", "Jira project key (e.g., 'PROJ', 'ACV2')").required();

  private final JiraRestClient client;

  public GetProjectVersionsTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "get_project_versions";
  }

  @Override
  public String description() {
    return "Get all fix versions for a specific Jira project.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  public List<ToolParam<?>> params() {
    return List.of(PROJECT_KEY);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String projectKey = args.require(PROJECT_KEY);

    return client.get("/rest/api/2/project/" + projectKey + "/versions", authHeader);
  }
}
