package com.atlassian.mcp.plugin.tools.forms;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import java.util.List;

public class GetProformaFormDetailsTool extends DeclarativeTool {

  private static final ToolParam<String> ISSUE_KEY =
      ToolParam.string("issue_key", "Jira issue key (e.g., 'PROJ-123')").required();
  private static final ToolParam<String> FORM_ID =
      ToolParam.string(
              "form_id", "ProForma form UUID (e.g., '1946b8b7-8f03-4dc0-ac2d-5fac0d960c6a')")
          .required();

  private final JiraRestClient client;

  public GetProformaFormDetailsTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "get_proforma_form_details";
  }

  @Override
  public String description() {
    return "Get detailed information about a specific ProForma form. Uses the new Jira Forms REST API. Returns form details including ADF design structure.";
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
    return List.of(ISSUE_KEY, FORM_ID);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String issueKey = args.require(ISSUE_KEY);
    args.require(FORM_ID);

    return client.get("/rest/api/2/issue/" + issueKey + "/properties/proforma.forms", authHeader);
  }
}
