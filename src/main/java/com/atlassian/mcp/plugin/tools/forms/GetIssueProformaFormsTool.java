package com.atlassian.mcp.plugin.tools.forms;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import java.util.List;

public class GetIssueProformaFormsTool extends DeclarativeTool {

  private static final ToolParam<String> ISSUE_KEY =
      ToolParam.string("issue_key", "Jira issue key (e.g., 'PROJ-123')").required();

  private final JiraRestClient client;

  public GetIssueProformaFormsTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "get_issue_proforma_forms";
  }

  @Override
  public String description() {
    return "Get all ProForma forms associated with a Jira issue. Uses the new Jira Forms REST API. Form IDs are returned as UUIDs.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  public String requiredPluginKey() {
    return "com.atlassian.jira.plugins.jira-proforma-plugin";
  }

  @Override
  public List<ToolParam<?>> params() {
    return List.of(ISSUE_KEY);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String issueKey = args.require(ISSUE_KEY);

    return client.get("/rest/api/2/issue/" + issueKey + "/properties/proforma.forms", authHeader);
  }
}
