package com.atlassian.mcp.plugin.tools.servicedesk;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import java.util.List;

public class GetServiceDeskForProjectTool extends DeclarativeTool {

  private static final ToolParam<String> PROJECT_KEY =
      ToolParam.string("project_key", "Jira project key (e.g., 'SUP')").required();

  private final JiraRestClient client;

  public GetServiceDeskForProjectTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "get_service_desk_for_project";
  }

  @Override
  public String description() {
    return "Get the Jira Service Desk associated with a project key. Server/Data Center only. Not available on Jira Cloud.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  public String requiredPluginKey() {
    return "com.atlassian.servicedesk";
  }

  @Override
  public List<ToolParam<?>> params() {
    return List.of(PROJECT_KEY);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    args.require(PROJECT_KEY);

    return client.get("/rest/servicedeskapi/servicedesk", authHeader);
  }
}
